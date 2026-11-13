package com.vidacruz.triage_service.application.service;

import com.vidacruz.triage_service.application.port.in.DepurarOutboxUseCase;
import com.vidacruz.triage_service.application.port.out.ClockPort;
import com.vidacruz.triage_service.application.port.out.OutboxPort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
public class DepurarOutboxService implements DepurarOutboxUseCase {

    private final OutboxPort outbox;
    private final ClockPort clock;

    public DepurarOutboxService(OutboxPort outbox, ClockPort clock) {
        this.outbox = outbox;
        this.clock = clock;
    }

    @Override
    public Mono<Long> ejecutar(Duration retencion) {
        return outbox.eliminarPublicadosAntesDe(clock.ahora().minus(retencion));
    }
}
