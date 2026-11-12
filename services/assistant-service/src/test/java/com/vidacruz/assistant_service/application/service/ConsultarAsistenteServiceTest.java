package com.vidacruz.assistant_service.application.service;

import com.vidacruz.assistant_service.application.port.out.LlmPort;
import com.vidacruz.assistant_service.application.port.out.ProtocolRetrieverPort;
import com.vidacruz.assistant_service.domain.model.Fuente;
import com.vidacruz.assistant_service.domain.model.FragmentoProtocolo;
import com.vidacruz.assistant_service.domain.model.RespuestaAsistente;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsultarAsistenteServiceTest {

    private static final double UMBRAL = 0.75;

    @Mock
    private ProtocolRetrieverPort retriever;

    @Mock
    private LlmPort llm;

    private ConsultarAsistenteService servicio;

    @BeforeEach
    void setUp() {
        servicio = new ConsultarAsistenteService(retriever, llm, UMBRAL);
    }

    @Test
    void responderConFuentesSacadasDeLaMetadataDeLosFragmentos() {
        when(retriever.recuperar(anyString())).thenReturn(Flux.just(
                new FragmentoProtocolo("PROTO-001", "Medidas iniciales", "texto", 0.91),
                new FragmentoProtocolo("PROTO-001", "Medidas iniciales", "otro", 0.88)));
        when(llm.responder(anyString(), anyList())).thenReturn(Mono.just("Reconfirmar la presión [PROTO-001]."));

        servicio.consultar("¿Qué hago con una presión de 190/110?")
                .as(StepVerifier::create)
                .assertNext(respuesta -> {
                    assertThat(respuesta.texto()).contains("[PROTO-001]");
                    assertThat(respuesta.fuentes()).containsExactly(new Fuente("PROTO-001", "Medidas iniciales"));
                })
                .verifyComplete();
    }

    @Test
    void noLlamaAlLlmCuandoNingunFragmentoSuperaElUmbral() {
        when(retriever.recuperar(anyString())).thenReturn(Flux.just(
                new FragmentoProtocolo("PROTO-002", "Criterios", "texto", 0.42)));

        servicio.consultar("¿Cómo cambio la batería del marcapasos?")
                .as(StepVerifier::create)
                .expectNext(RespuestaAsistente.sinProtocolo())
                .verifyComplete();

        verify(llm, never()).responder(any(), any());
    }

    @Test
    void noLlamaAlLlmCuandoElRecuperadorNoDevuelveNada() {
        when(retriever.recuperar(anyString())).thenReturn(Flux.empty());

        servicio.consultar("pregunta sin relación")
                .as(StepVerifier::create)
                .expectNext(RespuestaAsistente.sinProtocolo())
                .verifyComplete();

        verify(llm, never()).responder(any(), any());
    }

    @Test
    void respuestaVaciaDelProveedorSeConvierteEnAvisoSinProtocolo() {
        when(retriever.recuperar(anyString())).thenReturn(Flux.just(
                new FragmentoProtocolo("PROTO-003", "Clasificación", "texto", 0.9)));
        when(llm.responder(anyString(), anyList())).thenReturn(Mono.just("   "));

        servicio.consultar("deshidratación infantil")
                .as(StepVerifier::create)
                .expectNext(RespuestaAsistente.sinProtocolo())
                .verifyComplete();
    }

    @Test
    void respuestaQueCitaUnProtocoloFueraDelContextoSeConvierteEnAvisoSinProtocolo() {
        when(retriever.recuperar(anyString())).thenReturn(Flux.just(
                new FragmentoProtocolo("PROTO-003", "Clasificación", "texto", 0.9)));
        when(llm.responder(anyString(), anyList())).thenReturn(Mono.just("Según [PROTO-001], dar 20 ml/kg."));

        servicio.consultar("deshidratación infantil")
                .as(StepVerifier::create)
                .expectNext(RespuestaAsistente.sinProtocolo())
                .verifyComplete();
    }
}
