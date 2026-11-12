package com.vidacruz.triage_service.application.port.in;

import com.vidacruz.triage_service.domain.model.NivelTriaje;

import java.time.Instant;
import java.util.Map;

/**
 * Una hora del reporte, agrupada por la hora de registro. Los tiempos de espera son en minutos y valen
 * {@code null} si ningún paciente de esa hora llegó a consulta.
 */
public record FranjaHorariaVista(
        Instant hora,
        long totalRegistrados,
        Map<NivelTriaje, Long> porNivel,
        Double tiempoPromedioEsperaMin,
        Double p95EsperaMin,
        long rankingVolumen) {
}
