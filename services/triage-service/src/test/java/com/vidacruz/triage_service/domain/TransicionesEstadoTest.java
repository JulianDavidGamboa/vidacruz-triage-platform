package com.vidacruz.triage_service.domain;

import com.vidacruz.triage_service.domain.exception.TransicionInvalidaException;
import com.vidacruz.triage_service.domain.model.EstadoPaciente;
import com.vidacruz.triage_service.domain.model.EventoClinico;
import com.vidacruz.triage_service.domain.model.NivelTriaje;
import com.vidacruz.triage_service.domain.model.Paciente;
import com.vidacruz.triage_service.domain.model.SignosVitales;
import com.vidacruz.triage_service.domain.model.TipoEvento;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.util.UUID;

import static com.vidacruz.triage_service.domain.model.EstadoPaciente.ALTA;
import static com.vidacruz.triage_service.domain.model.EstadoPaciente.EN_CONSULTA;
import static com.vidacruz.triage_service.domain.model.EstadoPaciente.REGISTRADO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransicionesEstadoTest {

    private static final Instant REGISTRO = Instant.parse("2026-10-29T14:00:00Z");
    private static final Instant T1 = Instant.parse("2026-10-29T14:03:00Z");
    private static final Instant T2 = Instant.parse("2026-10-29T14:30:00Z");

    private static Paciente nuevo() {
        return Paciente.registrar(UUID.randomUUID(), "Carlos Gómez", 55,
                new SignosVitales(130, 180, 110, 97, 36.8, 8, 15), "Dolor torácico",
                NivelTriaje.CRITICO, 5, REGISTRO);
    }

    // ───────── matriz de transiciones ─────────

    @ParameterizedTest(name = "{0} → {1} es válida")
    @CsvSource({"REGISTRADO, EN_CONSULTA", "REGISTRADO, ALTA", "EN_CONSULTA, ALTA"})
    void transicionesValidas(EstadoPaciente origen, EstadoPaciente destino) {
        assertThat(origen.puedeTransicionarA(destino)).isTrue();
    }

    @ParameterizedTest(name = "{0} → {1} es inválida")
    @CsvSource({
            "ALTA, EN_CONSULTA", "ALTA, REGISTRADO", "ALTA, ALTA",
            "EN_CONSULTA, REGISTRADO", "EN_CONSULTA, EN_CONSULTA",
            "REGISTRADO, REGISTRADO"
    })
    void transicionesInvalidas(EstadoPaciente origen, EstadoPaciente destino) {
        assertThat(origen.puedeTransicionarA(destino)).isFalse();
    }

    // ───────── Paciente.cambiarEstado ─────────

    @Test
    void cambiarEstadoDevuelveNuevoPacienteYNoMutaElOriginal() {
        Paciente original = nuevo();

        Paciente enConsulta = original.cambiarEstado(EN_CONSULTA, T1);

        assertThat(enConsulta.estado()).isEqualTo(EN_CONSULTA);
        assertThat(enConsulta.fechaEstado()).isEqualTo(T1);
        assertThat(enConsulta.fechaRegistro()).isEqualTo(REGISTRO);
        assertThat(original.estado()).isEqualTo(REGISTRADO);
        assertThat(original.fechaPrimeraConsulta()).isNull();
    }

    @Test
    void laPrimeraConsultaSeFijaUnaSolaVez() {
        Paciente enConsulta = nuevo().cambiarEstado(EN_CONSULTA, T1);

        Paciente alta = enConsulta.cambiarEstado(ALTA, T2);

        assertThat(enConsulta.fechaPrimeraConsulta()).isEqualTo(T1);
        assertThat(alta.fechaPrimeraConsulta()).isEqualTo(T1);
    }

    @Test
    void altaDirectaNoFijaPrimeraConsulta() {
        assertThat(nuevo().cambiarEstado(ALTA, T1).fechaPrimeraConsulta()).isNull();
    }

    @Test
    void altaEsTerminal() {
        Paciente alta = nuevo().cambiarEstado(ALTA, T1);

        assertThatThrownBy(() -> alta.cambiarEstado(EN_CONSULTA, T2))
                .isInstanceOf(TransicionInvalidaException.class)
                .hasMessage("Transición inválida de ALTA a EN_CONSULTA");
        assertThatThrownBy(() -> alta.cambiarEstado(REGISTRADO, T2))
                .isInstanceOf(TransicionInvalidaException.class);
    }

    @Test
    void noSePuedeVolverARegistradoDesdeEnConsulta() {
        Paciente enConsulta = nuevo().cambiarEstado(EN_CONSULTA, T1);

        assertThatThrownBy(() -> enConsulta.cambiarEstado(REGISTRADO, T2))
                .isInstanceOf(TransicionInvalidaException.class);
    }

    @Test
    void elEscalamientoSeConservaEnLaTransicion() {
        Paciente escalado = new com.vidacruz.triage_service.domain.model.Paciente(
                UUID.randomUUID(), "Carlos Gómez", 55, new SignosVitales(130, 180, 110, 97, 36.8, 8, 15),
                "Dolor torácico", NivelTriaje.CRITICO, 5, REGISTRADO, true,
                REGISTRO, REGISTRO, T1, null);

        assertThat(escalado.cambiarEstado(EN_CONSULTA, T2).escalado()).isTrue();
    }

    @Test
    void minutosEnEstadoActualSeCalculaDesdeFechaEstado() {
        Paciente enConsulta = nuevo().cambiarEstado(EN_CONSULTA, T1);

        assertThat(enConsulta.minutosEnEstadoActual(T1.plusSeconds(600))).isEqualTo(10);
        assertThat(enConsulta.minutosEnEstadoActual(T1.minusSeconds(600))).isZero();
    }

    // ───────── eventos ─────────

    @Test
    void eventoDeEntradaAConsulta() {
        Paciente antes = nuevo();
        Paciente despues = antes.cambiarEstado(EN_CONSULTA, T1);

        EventoClinico evento = EventoClinico.cambioEstado(antes, despues, "cid-1");

        assertThat(evento.tipo()).isEqualTo(TipoEvento.PACIENTE_EN_CONSULTA);
        assertThat(evento.estadoAnterior()).isEqualTo(REGISTRADO);
        assertThat(evento.estadoNuevo()).isEqualTo(EN_CONSULTA);
        assertThat(evento.pacienteId()).isEqualTo(antes.id());
        assertThat(evento.nivel()).isEqualTo(NivelTriaje.CRITICO);
        assertThat(evento.ocurridoEn()).isEqualTo(T1);
        assertThat(evento.correlationId()).isEqualTo("cid-1");
    }

    @Test
    void eventoDeAlta() {
        Paciente enConsulta = nuevo().cambiarEstado(EN_CONSULTA, T1);

        EventoClinico evento = EventoClinico.cambioEstado(enConsulta, enConsulta.cambiarEstado(ALTA, T2), "cid-2");

        assertThat(evento.tipo()).isEqualTo(TipoEvento.PACIENTE_ALTA);
        assertThat(evento.estadoAnterior()).isEqualTo(EN_CONSULTA);
    }
}
