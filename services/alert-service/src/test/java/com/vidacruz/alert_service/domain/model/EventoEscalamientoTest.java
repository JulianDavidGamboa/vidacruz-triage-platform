package com.vidacruz.alert_service.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EventoEscalamientoTest {

    private static final Instant ESCALADO = Instant.parse("2026-11-12T10:00:00Z");

    @Test
    void minutosExcedidosEsElTiempoPasadoDelVencimiento() {
        EventoEscalamiento evento = evento(30, ESCALADO.minus(Duration.ofMinutes(72)));

        assertThat(evento.minutosExcedidos()).isEqualTo(42L);
    }

    @Test
    void sinDatosDeSlaNoHayMinutosExcedidos() {
        assertThat(evento(null, null).minutosExcedidos()).isNull();
    }

    @Test
    void nuncaSaleNegativoAunqueLosRelojesDifieran() {
        EventoEscalamiento evento = evento(30, ESCALADO.minus(Duration.ofMinutes(10)));

        assertThat(evento.minutosExcedidos()).isZero();
    }

    private EventoEscalamiento evento(Integer sla, Instant registro) {
        return new EventoEscalamiento(UUID.randomUUID(), UUID.randomUUID(), NivelTriaje.URGENTE, ESCALADO, sla,
                registro, "{}");
    }
}
