package com.vidacruz.triage_service.domain;

import com.vidacruz.triage_service.domain.model.EstadoPaciente;
import com.vidacruz.triage_service.domain.model.Escalamiento;
import com.vidacruz.triage_service.domain.model.EventoClinico;
import com.vidacruz.triage_service.domain.model.NivelTriaje;
import com.vidacruz.triage_service.domain.model.Paciente;
import com.vidacruz.triage_service.domain.model.SignosVitales;
import com.vidacruz.triage_service.domain.model.TipoEvento;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** RF-03 / RN-02 / RN-03 en el dominio. */
class EscalamientoDominioTest {

    private static final Instant T0 = Instant.parse("2026-10-29T14:00:00Z");

    private static Paciente registrado(int slaMinutos) {
        return Paciente.registrar(UUID.randomUUID(), "Carlos Gómez", 55,
                new SignosVitales(130, 180, 110, 97, 36.8, 8, 15), "Dolor torácico",
                NivelTriaje.CRITICO, slaMinutos, T0);
    }

    @Test
    void enElLimiteExactoDelSlaTodavíaNoVence() {
        assertThat(registrado(5).superaSla(T0.plusSeconds(5 * 60))).isFalse();
    }

    @Test
    void unSegundoDespuesDelSlaYaVence() {
        assertThat(registrado(5).superaSla(T0.plusSeconds(5 * 60 + 1))).isTrue();
    }

    @Test
    void antesDelSlaNoVence() {
        assertThat(registrado(5).superaSla(T0.plusSeconds(60))).isFalse();
    }

    @Test
    void unPacienteEnConsultaNuncaSeEscala() {
        Paciente enConsulta = registrado(5).cambiarEstado(EstadoPaciente.EN_CONSULTA, T0.plusSeconds(60));

        assertThat(enConsulta.superaSla(T0.plusSeconds(3600))).isFalse();
    }

    @Test
    void unPacienteDadoDeAltaNuncaSeEscala() {
        Paciente alta = registrado(5).cambiarEstado(EstadoPaciente.ALTA, T0.plusSeconds(60));

        assertThat(alta.superaSla(T0.plusSeconds(3600))).isFalse();
    }

    @Test
    void unPacienteYaEscaladoNoSeVuelveAEscalar() {
        Paciente base = registrado(5);
        Paciente escalado = new Paciente(base.id(), base.nombreCompleto(), base.edad(), base.signosVitales(),
                base.quejaPrincipal(), base.nivel(), base.slaMinutos(), EstadoPaciente.REGISTRADO, true,
                T0, T0, T0.plusSeconds(301), null);

        assertThat(escalado.superaSla(T0.plusSeconds(3600))).isFalse();
    }

    @Test
    void minutosExcedidosEsLaDiferenciaEntreElVencimientoYElEscalamiento() {
        Escalamiento e = new Escalamiento(UUID.randomUUID(), NivelTriaje.CRITICO, 5, T0, T0.plusSeconds(10 * 60));

        assertThat(e.minutosExcedidos()).isEqualTo(5);
    }

    @Test
    void minutosExcedidosNuncaEsNegativo() {
        Escalamiento e = new Escalamiento(UUID.randomUUID(), NivelTriaje.CRITICO, 5, T0, T0.plusSeconds(60));

        assertThat(e.minutosExcedidos()).isZero();
    }

    @Test
    void elEventoDeEscalamientoNoCambiaElEstado() {
        Escalamiento e = new Escalamiento(UUID.randomUUID(), NivelTriaje.URGENTE, 10, T0, T0.plusSeconds(11 * 60));

        EventoClinico evento = EventoClinico.escalamiento(e, "sla-1");

        assertThat(evento.tipo()).isEqualTo(TipoEvento.PACIENTE_ESCALADO);
        assertThat(evento.estadoAnterior()).isEqualTo(EstadoPaciente.REGISTRADO);
        assertThat(evento.estadoNuevo()).isEqualTo(EstadoPaciente.REGISTRADO);
        assertThat(evento.nivel()).isEqualTo(NivelTriaje.URGENTE);
        assertThat(evento.pacienteId()).isEqualTo(e.pacienteId());
        assertThat(evento.ocurridoEn()).isEqualTo(e.fechaEscalado());
        assertThat(evento.correlationId()).isEqualTo("sla-1");
    }
}
