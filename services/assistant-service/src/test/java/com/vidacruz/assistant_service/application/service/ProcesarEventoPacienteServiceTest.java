package com.vidacruz.assistant_service.application.service;

import com.vidacruz.assistant_service.application.port.out.VistaUrgenciasPort;
import com.vidacruz.assistant_service.domain.model.EventoPaciente;
import com.vidacruz.assistant_service.domain.model.NivelTriaje;
import com.vidacruz.assistant_service.domain.model.TipoEvento;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcesarEventoPacienteServiceTest {

    private final UUID pacienteId = UUID.randomUUID();

    @Mock
    private VistaUrgenciasPort vista;

    @Test
    void registroActivaAlPacienteConSuNivel() {
        when(vista.activar(any(), any())).thenReturn(Mono.empty());

        new ProcesarEventoPacienteService(vista)
                .procesar(evento(TipoEvento.PACIENTE_REGISTRADO, NivelTriaje.URGENTE))
                .as(StepVerifier::create)
                .verifyComplete();

        verify(vista).activar(eq(pacienteId), eq(NivelTriaje.URGENTE));
    }

    @Test
    void enConsultaVuelveAActivarConSuNivel() {
        when(vista.activar(any(), any())).thenReturn(Mono.empty());

        new ProcesarEventoPacienteService(vista)
                .procesar(evento(TipoEvento.PACIENTE_EN_CONSULTA, NivelTriaje.CRITICO))
                .as(StepVerifier::create)
                .verifyComplete();

        verify(vista).activar(eq(pacienteId), eq(NivelTriaje.CRITICO));
    }

    @Test
    void altaDesactivaAlPaciente() {
        when(vista.desactivar(any())).thenReturn(Mono.empty());

        new ProcesarEventoPacienteService(vista)
                .procesar(evento(TipoEvento.PACIENTE_ALTA, NivelTriaje.NO_URGENTE))
                .as(StepVerifier::create)
                .verifyComplete();

        verify(vista).desactivar(eq(pacienteId));
    }

    @Test
    void escaladoNoCambiaLaVista() {
        new ProcesarEventoPacienteService(vista)
                .procesar(evento(TipoEvento.PACIENTE_ESCALADO, NivelTriaje.URGENTE))
                .as(StepVerifier::create)
                .verifyComplete();

        verifyNoInteractions(vista);
        verify(vista, never()).activar(any(), any());
    }

    private EventoPaciente evento(TipoEvento tipo, NivelTriaje nivel) {
        return new EventoPaciente(UUID.randomUUID(), tipo, pacienteId, nivel);
    }
}
