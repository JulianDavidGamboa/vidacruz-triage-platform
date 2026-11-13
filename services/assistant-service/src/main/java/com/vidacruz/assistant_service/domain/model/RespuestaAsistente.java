package com.vidacruz.assistant_service.domain.model;

import java.util.List;
import java.util.Objects;

public record RespuestaAsistente(String texto, List<Fuente> fuentes) {

    public static final String SIN_PROTOCOLO =
            "No existe un protocolo disponible en el corpus para responder esta pregunta.";

    public RespuestaAsistente {
        Objects.requireNonNull(texto, "texto requerido");
        fuentes = List.copyOf(fuentes);
        if (fuentes.isEmpty() && !SIN_PROTOCOLO.equals(texto)) {
            throw new IllegalArgumentException("Una respuesta clínica debe citar al menos una fuente del corpus");
        }
    }

    public static RespuestaAsistente sinProtocolo() {
        return new RespuestaAsistente(SIN_PROTOCOLO, List.of());
    }
}
