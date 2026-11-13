package com.vidacruz.triage_service.infrastructure;

import com.vidacruz.triage_service.infrastructure.adapter.in.web.dto.RegistrarPacienteRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Validaciones de datos (tabla "Validaciones de datos" del enunciado). */
class RegistrarPacienteRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static final class Req {
        String nombre = "Ana María Pérez";
        Integer edad = 40, fc = 80, sist = 120, dias = 80, spo2 = 98, dolor = 3, gcs = 15;
        Double temp = 36.8;
        String queja = "Fiebre";

        RegistrarPacienteRequest build() {
            return new RegistrarPacienteRequest(nombre, edad, fc, sist, dias, spo2, temp, dolor, gcs, queja);
        }
    }

    private static Set<String> campos(Set<ConstraintViolation<RegistrarPacienteRequest>> violaciones) {
        return violaciones.stream().map(v -> v.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());
    }

    @Test
    void solicitudValidaNoTieneViolaciones() {
        assertThat(validator.validate(new Req().build())).isEmpty();
    }

    @Test
    void aceptaLosValoresMinimosPermitidos() {
        Req r = new Req();
        r.edad = 0; r.fc = 20; r.sist = 60; r.dias = 30; r.spo2 = 70; r.temp = 34.0; r.dolor = 0; r.gcs = 3;
        r.nombre = "Ana";

        assertThat(validator.validate(r.build())).isEmpty();
    }

    @Test
    void aceptaLosValoresMaximosPermitidos() {
        Req r = new Req();
        r.edad = 120; r.fc = 250; r.sist = 260; r.dias = 160; r.spo2 = 100; r.temp = 42.0; r.dolor = 10; r.gcs = 15;

        assertThat(validator.validate(r.build())).isEmpty();
    }

    @ParameterizedTest(name = "{0} = {1} es inválido")
    @CsvSource({
            "edad,-1", "edad,121",
            "frecuenciaCardiaca,19", "frecuenciaCardiaca,251",
            "presionSistolica,59", "presionSistolica,261",
            "presionDiastolica,29", "presionDiastolica,161",
            "spo2,69", "spo2,101",
            "temperatura,33.9", "temperatura,42.1",
            "dolor,-1", "dolor,11",
            "gcs,2", "gcs,16"
    })
    void rechazaValoresFueraDeRangoIndicandoElCampo(String campo, String valor) {
        Req r = new Req();
        switch (campo) {
            case "edad" -> r.edad = Integer.valueOf(valor);
            case "frecuenciaCardiaca" -> r.fc = Integer.valueOf(valor);
            case "presionSistolica" -> { r.sist = Integer.valueOf(valor); r.dias = 30; }
            case "presionDiastolica" -> r.dias = Integer.valueOf(valor);
            case "spo2" -> r.spo2 = Integer.valueOf(valor);
            case "temperatura" -> r.temp = Double.valueOf(valor);
            case "dolor" -> r.dolor = Integer.valueOf(valor);
            case "gcs" -> r.gcs = Integer.valueOf(valor);
            default -> throw new IllegalArgumentException(campo);
        }

        Set<ConstraintViolation<RegistrarPacienteRequest>> violaciones = validator.validate(r.build());

        assertThat(campos(violaciones)).contains(campo);
        assertThat(violaciones).anySatisfy(v -> assertThat(v.getMessage()).contains(campo));
    }

    @Test
    void rechazaDiastolicaMayorOIgualQueSistolica() {
        Req r = new Req();
        r.sist = 100;
        r.dias = 100;

        Set<ConstraintViolation<RegistrarPacienteRequest>> violaciones = validator.validate(r.build());

        assertThat(violaciones).hasSize(1);
        assertThat(violaciones.iterator().next().getMessage())
                .contains("presionDiastolica").contains("presionSistolica");
    }

    @ParameterizedTest
    @CsvSource({"Al", "''", "'   '"})
    void rechazaNombresDemasiadoCortosOVacios(String nombre) {
        Req r = new Req();
        r.nombre = nombre;

        assertThat(campos(validator.validate(r.build()))).contains("nombreCompleto");
    }

    @Test
    void rechazaNombreDeMasDe100Caracteres() {
        Req r = new Req();
        r.nombre = "a".repeat(101);

        assertThat(campos(validator.validate(r.build()))).contains("nombreCompleto");
    }

    @Test
    void rechazaCamposObligatoriosAusentes() {
        RegistrarPacienteRequest vacio =
                new RegistrarPacienteRequest(null, null, null, null, null, null, null, null, null, null);

        assertThat(campos(validator.validate(vacio))).containsExactlyInAnyOrder(
                "nombreCompleto", "edad", "frecuenciaCardiaca", "presionSistolica", "presionDiastolica",
                "spo2", "temperatura", "dolor", "gcs", "quejaPrincipal");
    }
}
