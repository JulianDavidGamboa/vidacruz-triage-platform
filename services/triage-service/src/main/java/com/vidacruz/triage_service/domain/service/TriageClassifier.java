package com.vidacruz.triage_service.domain.service;

import com.vidacruz.triage_service.domain.model.NivelTriaje;
import com.vidacruz.triage_service.domain.model.SignosVitales;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * RN-01 — Clasificación por nivel de triaje.
 * Las reglas se evalúan en orden de gravedad; la primera que se cumple determina el nivel, de modo
 * que si se cumplen condiciones de varios niveles rige el más grave. Es una clase pura (sin Spring).
 */
public final class TriageClassifier {

    private record Regla(NivelTriaje nivel, Predicate<SignosVitales> condicion) {
    }

    private static final List<Regla> REGLAS = List.of(
            new Regla(NivelTriaje.CRITICO, TriageClassifier::esCritico),
            new Regla(NivelTriaje.URGENTE, TriageClassifier::esUrgente),
            new Regla(NivelTriaje.URGENTE_2, TriageClassifier::esUrgente2));

    /** Ya normalizadas (minúsculas, sin tildes). */
    private static final Set<String> QUEJAS_ADMINISTRATIVAS = Set.of(
            "documentos",
            "recoleccion de resultados",
            "renovacion de recetario",
            "vacunacion",
            "seguimiento");

    public NivelTriaje clasificar(SignosVitales signos, String quejaPrincipal) {
        Objects.requireNonNull(signos, "signos vitales requeridos");
        for (Regla regla : REGLAS) {
            if (regla.condicion().test(signos)) {
                return regla.nivel();
            }
        }
        // Nivel 4 por defecto; pasa a nivel 5 solo si la queja es administrativa
        return esQuejaAdministrativa(quejaPrincipal) ? NivelTriaje.NO_URGENTE_2 : NivelTriaje.NO_URGENTE;
    }

    // Nivel 1: FC < 40 o FC > 120, o SpO2 < 90, o P. sistólica < 90, o GCS <= 8
    private static boolean esCritico(SignosVitales s) {
        return s.frecuenciaCardiaca() < 40
                || s.frecuenciaCardiaca() > 120
                || s.spo2() < 90
                || s.presionSistolica() < 90
                || s.gcs() <= 8;
    }

    // Nivel 2: 90 <= SpO2 <= 93, o 90 <= P. sistólica <= 100, o Temp >= 38.5, o 9 <= GCS <= 13
    private static boolean esUrgente(SignosVitales s) {
        return entre(s.spo2(), 90, 93)
                || entre(s.presionSistolica(), 90, 100)
                || s.temperatura() >= 38.5
                || entre(s.gcs(), 9, 13);
    }

    // Nivel 3: 90 <= FC <= 120, o 38.0 <= Temp < 38.5, o Dolor >= 7
    private static boolean esUrgente2(SignosVitales s) {
        return entre(s.frecuenciaCardiaca(), 90, 120)
                || (s.temperatura() >= 38.0 && s.temperatura() < 38.5)
                || s.dolor() >= 7;
    }

    private static boolean entre(int valor, int minimo, int maximo) {
        return valor >= minimo && valor <= maximo;
    }

    private static boolean esQuejaAdministrativa(String queja) {
        return QUEJAS_ADMINISTRATIVAS.contains(normalizar(queja));
    }

    /** Minúsculas, sin tildes y con espacios colapsados: "  Renovación  de Recetario " -> "renovacion de recetario". */
    static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return sinTildes.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}

