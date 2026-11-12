package com.vidacruz.triage_service.domain;

import com.vidacruz.triage_service.domain.exception.DatosClinicosInvalidosException;
import com.vidacruz.triage_service.domain.model.SignosVitales;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El dominio protege sus propios invariantes, sin depender del DTO web. */
class SignosVitalesTest {

    private static SignosVitales con(int fc, int sist, int dias, int spo2, double temp, int dolor, int gcs) {
        return new SignosVitales(fc, sist, dias, spo2, temp, dolor, gcs);
    }

    @Test
    void aceptaValoresMinimosYMaximos() {
        assertThatCode(() -> con(20, 60, 30, 70, 34.0, 0, 3)).doesNotThrowAnyException();
        assertThatCode(() -> con(250, 260, 160, 100, 42.0, 10, 15)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0} = {1} → inválido")
    @CsvSource({
            "frecuenciaCardiaca,19", "frecuenciaCardiaca,251",
            "presionSistolica,59", "presionSistolica,261",
            "presionDiastolica,29", "presionDiastolica,161",
            "spo2,69", "spo2,101",
            "temperatura,33.9", "temperatura,42.1",
            "dolor,-1", "dolor,11",
            "gcs,2", "gcs,16"
    })
    void rechazaValoresFueraDeRangoIndicandoElCampo(String campo, String valor) {
        int fc = 80, sist = 120, dias = 80, spo2 = 98, dolor = 3, gcs = 15;
        double temp = 36.8;
        switch (campo) {
            case "frecuenciaCardiaca" -> fc = Integer.parseInt(valor);
            case "presionSistolica" -> { sist = Integer.parseInt(valor); dias = 30; }
            case "presionDiastolica" -> dias = Integer.parseInt(valor);
            case "spo2" -> spo2 = Integer.parseInt(valor);
            case "temperatura" -> temp = Double.parseDouble(valor);
            case "dolor" -> dolor = Integer.parseInt(valor);
            case "gcs" -> gcs = Integer.parseInt(valor);
            default -> throw new IllegalArgumentException(campo);
        }
        final int f = fc, s = sist, d = dias, o = spo2, p = dolor, g = gcs;
        final double t = temp;

        assertThatThrownBy(() -> con(f, s, d, o, t, p, g))
                .isInstanceOfSatisfying(DatosClinicosInvalidosException.class, ex -> {
                    assertThat(ex.campo()).isEqualTo(campo);
                    assertThat(ex.getMessage()).contains(campo);
                });
    }

    @Test
    void rechazaDiastolicaMayorOIgualQueSistolica() {
        assertThatThrownBy(() -> con(80, 100, 100, 98, 36.8, 3, 15))
                .isInstanceOfSatisfying(DatosClinicosInvalidosException.class, ex -> {
                    assertThat(ex.campo()).isEqualTo("presionDiastolica");
                    assertThat(ex.getMessage()).contains("menor que presionSistolica");
                });
    }

    @Test
    void rechazaTemperaturaNaN() {
        assertThatThrownBy(() -> con(80, 120, 80, 98, Double.NaN, 3, 15))
                .isInstanceOf(DatosClinicosInvalidosException.class);
    }
}
