package com.vidacruz.assistant_service.domain.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

class RespuestaAsistenteTest {

    @Test
    void rechazaRespuestaClinicaSinFuentes() {
        assertThatThrownBy(() -> new RespuestaAsistente("Dosis de nifedipino", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aceptaAvisoSinProtocoloSinFuentes() {
        RespuestaAsistente respuesta = RespuestaAsistente.sinProtocolo();

        assertThat(respuesta.fuentes()).isEmpty();
        assertThat(respuesta.texto()).isEqualTo(RespuestaAsistente.SIN_PROTOCOLO);
    }

    @Test
    void copiaLasFuentesParaQueNoSePuedanMutarDesdeFuera() {
        List<Fuente> origen = new java.util.ArrayList<>(List.of(new Fuente("PROTO-001", "Definición")));
        RespuestaAsistente respuesta = new RespuestaAsistente("Texto", origen);

        origen.clear();

        assertThat(respuesta.fuentes()).hasSize(1);
    }
}
