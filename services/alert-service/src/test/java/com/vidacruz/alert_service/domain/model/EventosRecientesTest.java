package com.vidacruz.alert_service.domain.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventosRecientesTest {

    @Test
    void reconoceUnEventoYaRegistrado() {
        EventosRecientes recientes = new EventosRecientes(10);
        UUID evento = UUID.randomUUID();

        assertThat(recientes.contiene(evento)).isFalse();

        recientes.registrar(evento);

        assertThat(recientes.contiene(evento)).isTrue();
    }

    @Test
    void descartaLosMasAntiguosAlSuperarLaCapacidad() {
        EventosRecientes recientes = new EventosRecientes(2);
        UUID primero = UUID.randomUUID();
        UUID segundo = UUID.randomUUID();
        UUID tercero = UUID.randomUUID();

        recientes.registrar(primero);
        recientes.registrar(segundo);
        recientes.registrar(tercero);

        assertThat(recientes.contiene(primero)).isFalse();
        assertThat(recientes.contiene(segundo)).isTrue();
        assertThat(recientes.contiene(tercero)).isTrue();
    }

    @Test
    void rechazaCapacidadNoPositiva() {
        assertThatThrownBy(() -> new EventosRecientes(0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
