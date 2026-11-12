package com.vidacruz.triage_service.application.service;

import com.vidacruz.triage_service.application.CorrelationContext;
import com.vidacruz.triage_service.application.port.in.RegistrarPacienteCommand;
import com.vidacruz.triage_service.application.port.in.RegistrarPacienteUseCase;
import com.vidacruz.triage_service.application.port.out.*;
import com.vidacruz.triage_service.domain.model.EventoClinico;
import com.vidacruz.triage_service.domain.model.NivelTriaje;
import com.vidacruz.triage_service.domain.model.Paciente;
import com.vidacruz.triage_service.domain.service.TriageClassifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
public class RegistrarPacienteService implements RegistrarPacienteUseCase {

    private final TriageClassifier classifier;
    private final SlaPolicy slaPolicy;
    private final PacienteRepositoryPort pacientes;
    private final AuditPort auditoria;
    private final OutboxPort outbox;
    private final MetricsPort metrics;
    private final ClockPort clock;

    public RegistrarPacienteService(TriageClassifier classifier, SlaPolicy slaPolicy,
                                    PacienteRepositoryPort pacientes, AuditPort auditoria,
                                    OutboxPort outbox, MetricsPort metrics, ClockPort clock) {
        this.classifier = classifier;
        this.slaPolicy = slaPolicy;
        this.pacientes = pacientes;
        this.auditoria = auditoria;
        this.outbox = outbox;
        this.metrics = metrics;
        this.clock = clock;
    }

    /**
     * Paciente + auditoría + outbox se escriben en UNA transacción: o quedan los tres o ninguno.
     * El evento sale hacia Kafka después, desde el relay del outbox.
     */
    @Override
    @Transactional
    public Mono<Paciente> registrar(RegistrarPacienteCommand command) {
        return Mono.deferContextual(ctx -> {
            String correlationId = ctx.getOrDefault(CorrelationContext.KEY, "sin-correlacion");

            NivelTriaje nivel = classifier.clasificar(command.signosVitales(), command.quejaPrincipal());
            Paciente paciente = Paciente.registrar(
                    UUID.randomUUID(),
                    command.nombreCompleto(),
                    command.edad(),
                    command.signosVitales(),
                    command.quejaPrincipal(),
                    nivel,
                    slaPolicy.minutosPara(nivel),
                    clock.ahora());
            EventoClinico evento = EventoClinico.registro(paciente, correlationId);

            return pacientes.guardar(paciente)
                    .then(auditoria.registrar(evento))
                    .then(outbox.guardar(evento))
                    .doOnSuccess(ignored -> metrics.clasificacionRegistrada(nivel))
                    .thenReturn(paciente);
        });
    }
}
