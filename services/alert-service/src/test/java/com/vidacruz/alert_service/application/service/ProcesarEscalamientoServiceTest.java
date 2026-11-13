package com.vidacruz.alert_service.application.service;

import com.vidacruz.alert_service.application.port.out.AlertChannelPort;
import com.vidacruz.alert_service.application.port.out.DeadLetterPort;
import com.vidacruz.alert_service.domain.model.Alerta;
import com.vidacruz.alert_service.domain.model.EventoEscalamiento;
import com.vidacruz.alert_service.domain.model.EventosRecientes;
import com.vidacruz.alert_service.domain.model.NivelTriaje;
import com.vidacruz.alert_service.domain.model.ResultadoEnvio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcesarEscalamientoServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-12T10:00:00Z");

    private final UUID pacienteId = UUID.randomUUID();
    private final String crudo = "{\"type\":\"paciente_escalado\"}";

    @Mock
    private AlertChannelPort canal;

    @Mock
    private DeadLetterPort deadLetter;

    private EventosRecientes recientes;
    private ProcesarEscalamientoService servicio;

    @BeforeEach
    void setUp() {
        recientes = new EventosRecientes(100);
        servicio = new ProcesarEscalamientoService(canal, deadLetter, recientes);
    }

    @Test
    void alertaUnaVezYNoVuelveAEnviarUnEventoRepetido() {
        EventoEscalamiento evento = evento();
        when(canal.enviar(any())).thenReturn(Mono.just(ResultadoEnvio.ENVIADA));

        servicio.procesar(evento).block();
        servicio.procesar(evento).block();

        verify(canal, org.mockito.Mockito.times(1)).enviar(any());
        verifyNoInteractions(deadLetter);
    }

    @Test
    void envioExitosoNoPublicaEnElDlt() {
        when(canal.enviar(any())).thenReturn(Mono.just(ResultadoEnvio.ENVIADA));

        servicio.procesar(evento())
                .as(StepVerifier::create)
                .verifyComplete();

        verifyNoInteractions(deadLetter);
    }

    @Test
    void sinWebhookSoloQuedaEnLogYNoVaAlDlt() {
        when(canal.enviar(any())).thenReturn(Mono.just(ResultadoEnvio.SOLO_LOG));

        servicio.procesar(evento())
                .as(StepVerifier::create)
                .verifyComplete();

        verifyNoInteractions(deadLetter);
    }

    @Test
    void fallaDeTeamsPublicaElRegistroOriginalEnElDlt() {
        EventoEscalamiento evento = evento();
        when(canal.enviar(any())).thenReturn(Mono.just(ResultadoEnvio.REGISTRADA_EN_LOG));
        when(deadLetter.publicar(any(), any())).thenReturn(Mono.empty());

        servicio.procesar(evento).block();

        verify(deadLetter).publicar(eq(pacienteId.toString()), eq(crudo));
    }

    @Test
    void errorInesperadoNoMarcaElEventoComoVisto() {
        EventoEscalamiento evento = evento();
        when(canal.enviar(any())).thenReturn(Mono.error(new IllegalStateException("boom")));

        servicio.procesar(evento)
                .as(StepVerifier::create)
                .expectError(IllegalStateException.class)
                .verify();

        org.assertj.core.api.Assertions.assertThat(recientes.contiene(evento.eventId())).isFalse();
        verify(deadLetter, never()).publicar(any(), any());
    }

    @Test
    void laAlertaLlevaSoloIdentificadorNivelYMarcaDeTiempo() {
        EventoEscalamiento evento = evento();
        when(canal.enviar(any())).thenReturn(Mono.just(ResultadoEnvio.ENVIADA));

        servicio.procesar(evento).block();

        verify(canal).enviar(new Alerta(pacienteId, NivelTriaje.URGENTE, AHORA, 30, 42L));
    }

    @Test
    void mensajeVersion1SinSlaSeAlertaSinEsosDatos() {
        EventoEscalamiento sinSla = new EventoEscalamiento(UUID.randomUUID(), pacienteId, NivelTriaje.URGENTE,
                AHORA, null, null, crudo);
        when(canal.enviar(any())).thenReturn(Mono.just(ResultadoEnvio.ENVIADA));

        servicio.procesar(sinSla).block();

        verify(canal).enviar(new Alerta(pacienteId, NivelTriaje.URGENTE, AHORA, null, null));
    }

    private EventoEscalamiento evento() {
        return new EventoEscalamiento(UUID.randomUUID(), pacienteId, NivelTriaje.URGENTE, AHORA, 30,
                AHORA.minus(Duration.ofMinutes(72)), crudo);
    }
}
