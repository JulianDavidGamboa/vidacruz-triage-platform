package com.vidacruz.triage_service.infrastructure.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.*;

/** Cuerpo de POST /triage/pacientes. Los rangos salen de la tabla "Validaciones de datos" del enunciado. */
public record RegistrarPacienteRequest(

        @NotBlank(message = "nombreCompleto es obligatorio")
        @Size(min = 3, max = 100, message = "nombreCompleto debe tener entre 3 y 100 caracteres")
        String nombreCompleto,

        @NotNull(message = "edad es obligatoria")
        @Min(value = 0, message = "edad debe estar entre 0 y 120 años")
        @Max(value = 120, message = "edad debe estar entre 0 y 120 años")
        Integer edad,

        @NotNull(message = "frecuenciaCardiaca es obligatoria")
        @Min(value = 20, message = "frecuenciaCardiaca debe estar entre 20 y 250 bpm")
        @Max(value = 250, message = "frecuenciaCardiaca debe estar entre 20 y 250 bpm")
        Integer frecuenciaCardiaca,

        @NotNull(message = "presionSistolica es obligatoria")
        @Min(value = 60, message = "presionSistolica debe estar entre 60 y 260 mmHg")
        @Max(value = 260, message = "presionSistolica debe estar entre 60 y 260 mmHg")
        Integer presionSistolica,

        @NotNull(message = "presionDiastolica es obligatoria")
        @Min(value = 30, message = "presionDiastolica debe estar entre 30 y 160 mmHg")
        @Max(value = 160, message = "presionDiastolica debe estar entre 30 y 160 mmHg")
        Integer presionDiastolica,

        @NotNull(message = "spo2 es obligatorio")
        @Min(value = 70, message = "spo2 debe estar entre 70 y 100 %")
        @Max(value = 100, message = "spo2 debe estar entre 70 y 100 %")
        Integer spo2,

        @NotNull(message = "temperatura es obligatoria")
        @DecimalMin(value = "34.0", message = "temperatura debe estar entre 34.0 y 42.0 °C")
        @DecimalMax(value = "42.0", message = "temperatura debe estar entre 34.0 y 42.0 °C")
        Double temperatura,

        @NotNull(message = "dolor es obligatorio")
        @Min(value = 0, message = "dolor debe estar entre 0 y 10")
        @Max(value = 10, message = "dolor debe estar entre 0 y 10")
        Integer dolor,

        @NotNull(message = "gcs es obligatorio")
        @Min(value = 3, message = "gcs debe estar entre 3 y 15")
        @Max(value = 15, message = "gcs debe estar entre 3 y 15")
        Integer gcs,

        @NotBlank(message = "quejaPrincipal es obligatoria")
        @Size(max = 300, message = "quejaPrincipal no puede superar 300 caracteres")
        String quejaPrincipal) {

    /** Regla cruzada: la diastólica debe ser menor que la sistólica. Si falta alguna, lo reporta @NotNull. */
    @JsonIgnore
    @AssertTrue(message = "presionDiastolica debe ser menor que presionSistolica")
    public boolean isPresionDiastolicaMenorQueSistolica() {
        if (presionSistolica == null || presionDiastolica == null) {
            return true;
        }
        return presionDiastolica < presionSistolica;
    }
}

