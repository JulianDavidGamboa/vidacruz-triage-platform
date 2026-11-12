package com.vidacruz.triage_service.domain.model;

import com.vidacruz.triage_service.domain.exception.DatosClinicosInvalidosException;

/**
 * Signos vitales del paciente. Los invariantes (rangos del enunciado) se garantizan aquí, de modo que
 * ningún adaptador —REST hoy, Kafka o gRPC mañana— pueda crear signos inválidos. El DTO web valida
 * lo mismo antes, solo para dar mensajes por campo más tempranos.
 */
public record SignosVitales(
        int frecuenciaCardiaca,   // 20–250 bpm
        int presionSistolica,     // 60–260 mmHg
        int presionDiastolica,    // 30–160 mmHg, siempre < sistólica
        int spo2,                 // 70–100 %
        double temperatura,       // 34.0–42.0 °C
        int dolor,                // 0–10
        int gcs) {                // 3–15

    public SignosVitales {
        Validacion.entre("frecuenciaCardiaca", frecuenciaCardiaca, 20, 250, "bpm");
        Validacion.entre("presionSistolica", presionSistolica, 60, 260, "mmHg");
        Validacion.entre("presionDiastolica", presionDiastolica, 30, 160, "mmHg");
        if (presionDiastolica >= presionSistolica) {
            throw new DatosClinicosInvalidosException("presionDiastolica",
                    "presionDiastolica debe ser menor que presionSistolica");
        }
        Validacion.entre("spo2", spo2, 70, 100, "%");
        Validacion.entre("temperatura", temperatura, 34.0, 42.0, "°C");
        Validacion.entre("dolor", dolor, 0, 10, "");
        Validacion.entre("gcs", gcs, 3, 15, "");
    }
}

