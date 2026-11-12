package com.vidacruz.assistant_service.infrastructure.adapter.out.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parte un protocolo en fragmentos, uno por viñeta ({@code - **Sección:** texto}). El identificador y el título salen
 * de la primera línea de encabezado. Cada fragmento lleva el protocolo y el título en el texto, para que la búsqueda
 * encuentre también preguntas por el nombre de la enfermedad.
 */
final class ProtocoloParser {

    private static final Pattern ID = Pattern.compile("PROTO-\\d{3}");
    private static final Pattern TITULO = Pattern.compile("^#+ .*?— (.+)$", Pattern.MULTILINE);
    private static final Pattern VINETA = Pattern.compile("^- \\*\\*(.+?):\\*\\* (.+)$", Pattern.MULTILINE);

    private ProtocoloParser() {
    }

    static List<Fragmento> parsear(String contenido) {
        Matcher id = ID.matcher(contenido);
        if (!id.find()) {
            throw new IllegalArgumentException("El protocolo no declara un identificador PROTO-NNN");
        }
        Matcher titulo = TITULO.matcher(contenido);
        String tituloProtocolo = titulo.find() ? titulo.group(1).trim() : id.group();

        List<Fragmento> fragmentos = new ArrayList<>();
        Matcher vineta = VINETA.matcher(contenido);
        while (vineta.find()) {
            String seccion = vineta.group(1).trim();
            String descripcion = vineta.group(2).trim();
            String texto = "%s (%s). %s: %s".formatted(id.group(), tituloProtocolo, seccion, descripcion);
            fragmentos.add(new Fragmento(id.group(), seccion, texto));
        }
        return fragmentos;
    }

    record Fragmento(String protocolo, String seccion, String texto) {
    }
}
