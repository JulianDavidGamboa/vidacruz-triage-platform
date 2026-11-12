package com.vidacruz.triage_service.infrastructure.adapter.in.scheduler;

import com.vidacruz.triage_service.application.CorrelationContext;
import com.vidacruz.triage_service.application.port.in.EscalarPacientesVencidosUseCase;
import com.vidacruz.triage_service.infrastructure.config.TriageProperties;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;


/**
 * RF-03: adaptador de entrada que dispara el escalamiento periódicamente. concatMap evita que dos
 * ejecuciones se solapen dentro de la misma instancia; entre réplicas lo resuelve SKIP LOCKED.
 */
@Component
@ConditionalOnProperty(name = "triage.sla.monitor-enabled", havingValue = "true", matchIfMissing = true)
public class SlaMonitorScheduler {

    private static final Logger log = LoggerFactory.getLogger(SlaMonitorScheduler.class);

    private final EscalarPacientesVencidosUseCase escalar;
    private final TriageProperties props;
    private Disposable subscription;

    public SlaMonitorScheduler(EscalarPacientesVencidosUseCase escalar, TriageProperties props) {
        this.escalar = escalar;
        this.props = props;
    }

    @EventListener(ApplicationReadyEvent.class)
    void start() {
        subscription = Flux.interval(props.sla().evaluationInterval())
                // Un ciclo lento no encola ticks atrasados: sin esto, el overflow termina el monitor en silencio
                .onBackpressureDrop()
                .concatMap(tick -> escalar.ejecutar()
                        // Un identificador por ejecución: agrupa en logs y auditoría las escalas de un mismo ciclo
                        .contextWrite(ctx -> ctx.put(CorrelationContext.KEY, "sla-" + UUID.randomUUID()))
                        .doOnNext(n -> {
                            if (n > 0) {
                                log.info("Escalados {} pacientes por SLA vencido", n);
                            }
                        })
                        .onErrorResume(e -> {
                            log.warn("Monitor de SLA falló; se reintentará en el siguiente ciclo: {}", e.toString());
                            return Mono.empty();
                        }))
                .subscribe();
    }

    @PreDestroy
    void stop() {
        if (subscription != null) {
            subscription.dispose();
        }
    }
}
