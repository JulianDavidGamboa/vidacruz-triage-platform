package com.vidacruz.assistant_service.integration;

import com.vidacruz.assistant_service.application.port.out.VistaUrgenciasPort;
import com.vidacruz.assistant_service.domain.model.EstadoUrgencias;
import com.vidacruz.assistant_service.domain.model.NivelTriaje;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** El Redis es compartido entre pruebas, así que cada una compara contra su propio estado inicial. */
class VistaUrgenciasRedisIT extends IntegrationTestBase {

    @Autowired
    private VistaUrgenciasPort vista;

    @Test
    void activarSumaUnPacienteEnSuNivelYDarDeAltaLoQuita() {
        EstadoUrgencias antes = vista.leer().block();
        UUID paciente = UUID.randomUUID();

        vista.activar(paciente, NivelTriaje.URGENTE).block();
        EstadoUrgencias despuesDeActivar = vista.leer().block();

        assertThat(despuesDeActivar.total()).isEqualTo(antes.total() + 1);
        assertThat(despuesDeActivar.porNivel().get(NivelTriaje.URGENTE))
                .isEqualTo(antes.porNivel().get(NivelTriaje.URGENTE) + 1);

        vista.desactivar(paciente).block();
        EstadoUrgencias despuesDeAlta = vista.leer().block();

        assertThat(despuesDeAlta.total()).isEqualTo(antes.total());
    }

    @Test
    void activarDosVecesNoDuplicaAlPaciente() {
        EstadoUrgencias antes = vista.leer().block();
        UUID paciente = UUID.randomUUID();

        vista.activar(paciente, NivelTriaje.CRITICO).block();
        vista.activar(paciente, NivelTriaje.CRITICO).block();
        EstadoUrgencias despues = vista.leer().block();

        assertThat(despues.total()).isEqualTo(antes.total() + 1);
    }
}
