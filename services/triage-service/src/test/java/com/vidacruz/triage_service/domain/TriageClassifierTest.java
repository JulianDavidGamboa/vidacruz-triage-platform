package com.vidacruz.triage_service.domain;

import com.vidacruz.triage_service.domain.model.NivelTriaje;
import com.vidacruz.triage_service.domain.model.SignosVitales;
import com.vidacruz.triage_service.domain.service.TriageClassifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class TriageClassifierTest {

    private final TriageClassifier classifier = new TriageClassifier();

    /** Paciente "sano" base; cada test modifica un solo valor. */
    private static final class Signos {
        int fc = 72, sist = 120, dias = 80, spo2 = 99, dolor = 2, gcs = 15;
        double temp = 36.5;

        Signos fc(int v) { fc = v; return this; }
        Signos sist(int v) { sist = v; return this; }
        Signos spo2(int v) { spo2 = v; return this; }
        Signos dolor(int v) { dolor = v; return this; }
        Signos gcs(int v) { gcs = v; return this; }
        Signos temp(double v) { temp = v; return this; }

        SignosVitales build() {
            return new SignosVitales(fc, sist, dias, spo2, temp, dolor, gcs);
        }
    }

    // ───────── Pacientes de ejemplo del enunciado (E1–E6) ─────────

    @ParameterizedTest(name = "{0} → {9}")
    @CsvSource(delimiter = '|', textBlock = """
            E1 | 130 | 180 | 110 | 97 | 36.8 | 8 | 15 | Dolor torácico           | CRITICO
            E2 | 95  | 150 | 95  | 91 | 37.0 | 4 | 15 | Dificultad para respirar | URGENTE
            E3 | 88  | 130 | 85  | 98 | 38.2 | 6 | 15 | Fiebre                   | URGENTE_2
            E4 | 72  | 120 | 80  | 99 | 36.5 | 2 | 15 | Herida por cortar        | NO_URGENTE
            E5 | 70  | 115 | 75  | 99 | 36.4 | 0 | 15 | Renovación de recetario  | NO_URGENTE_2
            E6 | 35  | 80  | 50  | 85 | 35.9 | 0 | 6  | Inconsciente             | CRITICO
            """)
    void clasificaPacientesDeEjemplo(String id, int fc, int sist, int dias, int spo2, double temp,
                                     int dolor, int gcs, String queja, NivelTriaje esperado) {
        SignosVitales signos = new SignosVitales(fc, sist, dias, spo2, temp, dolor, gcs);

        assertThat(classifier.clasificar(signos, queja)).isEqualTo(esperado);
    }

    // ───────── Valores límite de cada condición ─────────

    @ParameterizedTest(name = "{0}={1} → {2}")
    @CsvSource(delimiter = '|', textBlock = """
            fc    | 39   | CRITICO
            fc    | 40   | NO_URGENTE
            fc    | 89   | NO_URGENTE
            fc    | 90   | URGENTE_2
            fc    | 120  | URGENTE_2
            fc    | 121  | CRITICO
            spo2  | 89   | CRITICO
            spo2  | 90   | URGENTE
            spo2  | 93   | URGENTE
            spo2  | 94   | NO_URGENTE
            sist  | 89   | CRITICO
            sist  | 90   | URGENTE
            sist  | 100  | URGENTE
            sist  | 101  | NO_URGENTE
            gcs   | 8    | CRITICO
            gcs   | 9    | URGENTE
            gcs   | 13   | URGENTE
            gcs   | 14   | NO_URGENTE
            temp  | 37.9 | NO_URGENTE
            temp  | 38.0 | URGENTE_2
            temp  | 38.4 | URGENTE_2
            temp  | 38.5 | URGENTE
            dolor | 6    | NO_URGENTE
            dolor | 7    | URGENTE_2
            """)
    void respetaLosLimitesDeCadaRegla(String campo, String valor, NivelTriaje esperado) {
        Signos signos = new Signos();
        switch (campo) {
            case "fc" -> signos.fc(Integer.parseInt(valor));
            case "spo2" -> signos.spo2(Integer.parseInt(valor));
            case "sist" -> signos.sist(Integer.parseInt(valor));
            case "gcs" -> signos.gcs(Integer.parseInt(valor));
            case "temp" -> signos.temp(Double.parseDouble(valor));
            case "dolor" -> signos.dolor(Integer.parseInt(valor));
            default -> throw new IllegalArgumentException(campo);
        }

        assertThat(classifier.clasificar(signos.build(), "Herida por cortar")).isEqualTo(esperado);
    }

    // ───────── Precedencia: rige el nivel de mayor gravedad ─────────

    @Test
    void siSeCumplenVariosNivelesRigeElMasGrave() {
        // FC 130 (nivel 1) + SpO2 91 (nivel 2) + dolor 9 (nivel 3)
        SignosVitales signos = new Signos().fc(130).spo2(91).dolor(9).build();

        assertThat(classifier.clasificar(signos, "Dolor torácico")).isEqualTo(NivelTriaje.CRITICO);
    }

    @Test
    void nivel2GanaSobreNivel3() {
        // SpO2 91 (nivel 2) + FC 95 (nivel 3)
        SignosVitales signos = new Signos().spo2(91).fc(95).build();

        assertThat(classifier.clasificar(signos, "Mareo")).isEqualTo(NivelTriaje.URGENTE);
    }

    // ───────── Nivel 5: quejas administrativas ─────────

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            documentos
            Recolección de resultados
            RENOVACIÓN DE RECETARIO
            vacunacion
            Seguimiento
            '   renovación    de   recetario  '
            """)
    void quejaAdministrativaConSignosNormalesEsNivel5(String queja) {
        assertThat(classifier.clasificar(new Signos().build(), queja)).isEqualTo(NivelTriaje.NO_URGENTE_2);
    }

    @Test
    void quejaAdministrativaConSignosAnormalesNoBajaDeNivel() {
        SignosVitales conDolor = new Signos().dolor(8).build();

        assertThat(classifier.clasificar(conDolor, "Seguimiento")).isEqualTo(NivelTriaje.URGENTE_2);
    }

    @Test
    void quejaQueSoloContieneUnaPalabraAdministrativaNoCuenta() {
        // Supuesto documentado: la coincidencia es exacta (normalizada), no por "contiene"
        assertThat(classifier.clasificar(new Signos().build(), "Seguimiento de herida"))
                .isEqualTo(NivelTriaje.NO_URGENTE);
    }

    @Test
    void quejaNulaSeTrataComoNoAdministrativa() {
        assertThat(classifier.clasificar(new Signos().build(), null)).isEqualTo(NivelTriaje.NO_URGENTE);
    }
}
