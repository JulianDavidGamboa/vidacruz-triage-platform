package com.vidacruz.triage_service.domain.model;

import com.vidacruz.triage_service.domain.exception.DatosClinicosInvalidosException;

/** Helpers de validación de invariantes. Los mensajes coinciden con los del DTO de entrada. */
final class Validacion {

    private Validacion() {
    }

    static void entre(String campo, int valor, int minimo, int maximo, String unidad) {
        if (valor < minimo || valor > maximo) {
            throw rango(campo, String.valueOf(minimo), String.valueOf(maximo), unidad);
        }
    }

    static void entre(String campo, double valor, double minimo, double maximo, String unidad) {
        if (Double.isNaN(valor) || valor < minimo || valor > maximo) {
            throw rango(campo, String.valueOf(minimo), String.valueOf(maximo), unidad);
        }
    }

    static void longitud(String campo, String valor, int minimo, int maximo) {
        if (valor == null || valor.length() < minimo || valor.length() > maximo) {
            throw new DatosClinicosInvalidosException(campo,
                    "%s debe tener entre %d y %d caracteres".formatted(campo, minimo, maximo));
        }
    }

    static void obligatorioConMaximo(String campo, String valor, int maximo) {
        if (valor == null || valor.isBlank()) {
            throw new DatosClinicosInvalidosException(campo, campo + " es obligatoria");
        }
        if (valor.length() > maximo) {
            throw new DatosClinicosInvalidosException(campo,
                    "%s no puede superar %d caracteres".formatted(campo, maximo));
        }
    }

    private static DatosClinicosInvalidosException rango(String campo, String min, String max, String unidad) {
        String sufijo = unidad.isEmpty() ? "" : " " + unidad;
        return new DatosClinicosInvalidosException(campo,
                "%s debe estar entre %s y %s%s".formatted(campo, min, max, sufijo));
    }
}
