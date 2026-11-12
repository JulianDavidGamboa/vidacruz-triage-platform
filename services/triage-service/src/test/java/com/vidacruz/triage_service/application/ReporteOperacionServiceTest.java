package com.vidacruz.triage_service.application;

import com.vidacruz.triage_service.application.port.in.FranjaHorariaVista;
import com.vidacruz.triage_service.application.port.out.ReporteOperacionPort;
import com.vidacruz.triage_service.application.service.ReporteOperacionService;
import com.vidacruz.triage_service.domain.exception.RangoReporteInvalidoException;
import com.vidacruz.triage_service.domain.model.NivelTriaje;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReporteOperacionServiceTest {

    private static final Instant DESDE = Instant.parse("2026-10-29T00:00:00Z");
    private static final Instant HASTA = Instant.parse("2026-10-29T06:00:00Z");

    @Mock ReporteOperacionPort reportes;

    @Test
    void rechazaUnRangoVacioOInvertidoSinConsultarLaBase() {
        ReporteOperacionService service = new ReporteOperacionService(reportes);

        StepVerifier.create(service.generar(HASTA, DESDE))
                .expectError(RangoReporteInvalidoException.class)
                .verify();
        StepVerifier.create(service.generar(DESDE, DESDE))
                .expectError(RangoReporteInvalidoException.class)
                .verify();

        verifyNoInteractions(reportes);
    }

    @Test
    void devuelveLasFranjasDelPuertoEnElMismoRango() {
        FranjaHorariaVista franja = new FranjaHorariaVista(
                DESDE, 2, Map.of(NivelTriaje.CRITICO, 1L, NivelTriaje.URGENTE, 1L),
                12.5, 20.0, 1L);
        when(reportes.resumirPorHora(DESDE, HASTA)).thenReturn(Flux.just(franja));

        ReporteOperacionService service = new ReporteOperacionService(reportes);

        StepVerifier.create(service.generar(DESDE, HASTA))
                .assertNext(vista -> {
                    assertThat(vista.desde()).isEqualTo(DESDE);
                    assertThat(vista.hasta()).isEqualTo(HASTA);
                    assertThat(vista.franjas()).containsExactly(franja);
                })
                .verifyComplete();
    }
}
