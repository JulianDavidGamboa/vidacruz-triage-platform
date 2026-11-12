package com.vidacruz.triage_service.application.port.out;

import java.time.Instant;

/** Fuente de tiempo del dominio: en producción es el reloj del sistema; en tests, un instante fijo. */
public interface ClockPort {

    Instant ahora();
}
