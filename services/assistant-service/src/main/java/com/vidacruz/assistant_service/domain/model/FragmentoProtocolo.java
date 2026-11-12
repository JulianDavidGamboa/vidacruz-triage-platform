package com.vidacruz.assistant_service.domain.model;

/** Fragmento recuperado del corpus, con su similitud con la pregunta. */
public record FragmentoProtocolo(String protocolo, String seccion, String texto, double similitud) {
}
