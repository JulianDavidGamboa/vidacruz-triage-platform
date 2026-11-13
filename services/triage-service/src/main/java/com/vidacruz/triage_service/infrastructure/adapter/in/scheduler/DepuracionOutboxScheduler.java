package com.vidacruz.triage_service.infrastructure.adapter.in.scheduler;

import com.vidacruz.triage_service.application.port.in.DepurarOutboxUseCase;
import com.vidacruz.triage_service.infrastructure.config.TriageProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@ConditionalOnProperty(name = "triage.outbox.purge-enabled", havingValue = "true", matchIfMissing = true)
public class DepuracionOutboxScheduler {

    private static final Logger log = LoggerFactory.getLogger(DepuracionOutboxScheduler.class);

    private final DepurarOutboxUseCase depurar;
    private final TriageProperties props;
    private Disposable subscription;

    public DepuracionOutboxScheduler(DepurarOutboxUseCase depurar, TriageProperties props) {
        this.depurar = depurar;
        this.props = props;
    }

    @EventListener(ApplicationReadyEvent.class)
    void start() {
        subscription = Flux.interval(props.outbox().purgeInterval())
                .onBackpressureDrop()
                .concatMap(tick -> depurar.ejecutar(props.outbox().retention())
                        .doOnNext(n -> {
                            if (n > 0) {
                                log.info("Depurados {} eventos publicados del outbox", n);
                            }
                        })
                        .onErrorResume(e -> {
                            log.warn("Depuración del outbox falló; se reintentará en el siguiente ciclo: {}", e.toString());
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
