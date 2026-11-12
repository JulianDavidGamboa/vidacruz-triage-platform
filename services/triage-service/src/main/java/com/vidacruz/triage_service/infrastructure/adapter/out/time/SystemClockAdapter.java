package com.vidacruz.triage_service.infrastructure.adapter.out.time;

import com.vidacruz.triage_service.application.port.out.ClockPort;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/** Adaptador del reloj de sistema (UTC). Los tests reemplazan el bean {@code Clock} por uno fijo. */
@Component
public class SystemClockAdapter implements ClockPort {

    private final Clock clock;

    public SystemClockAdapter(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Instant ahora() {
        return clock.instant();
    }
}
