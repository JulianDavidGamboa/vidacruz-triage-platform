package com.vidacruz.triage_service.domain;

import com.vidacruz.triage_service.domain.exception.DatosClinicosInvalidosException;
import com.vidacruz.triage_service.domain.model.EstadoPaciente;
import com.vidacruz.triage_service.domain.model.EventoClinico;
import com.vidacruz.triage_service.domain.model.NivelTriaje;
import com.vidacruz.triage_service.domain.model.Paciente;
import com.vidacruz.triage_service.domain.model.SignosVitales;
import com.vidacruz.triage_service.domain.model.TipoEvento;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PacienteTest {

    private static final Instant AHORA = Instant.parse("2026-10-29T14:00:00Z");
    private static final SignosVitales SIGNOS = new SignosVitales(80, 120, 80, 98, 36.8, 3, 15);

    private static Paciente registrar(String nombre, int edad, String queja, int sla) {
        return Paciente.registrar(UUID.randomUUID(), nombre, edad, SIGNOS, queja, NivelTriaje.NO_URGENTE, sla, AHORA);
    }

    @Test
    void unPacienteRecienRegistradoQuedaEnRegistradoSinEscalar() {
        Paciente p = registrar("  Ana María Pérez ", 40, " Fiebre ", 60);

        assertThat(p.estado()).isEqualTo(EstadoPaciente.REGISTRADO);
        assertThat(p.escalado()).isFalse();
        assertThat(p.nombreCompleto()).isEqualTo("Ana María Pérez");
        assertThat(p.quejaPrincipal()).isEqualTo("Fiebre");
        assertThat(p.fechaRegistro()).isEqualTo(AHORA);
        assertThat(p.fechaEstado()).isEqualTo(AHORA);
        assertThat(p.fechaEscalado()).isNull();
        assertThat(p.fechaPrimeraConsulta()).isNull();
    }

    @Test
    void rechazaNombreCortoOLargo() {
        assertThatThrownBy(() -> registrar("Al", 40, "Fiebre", 60))
                .isInstanceOfSatisfying(DatosClinicosInvalidosException.class,
                        ex -> assertThat(ex.campo()).isEqualTo("nombreCompleto"));
        assertThatThrownBy(() -> registrar("a".repeat(101), 40, "Fiebre", 60))
                .isInstanceOf(DatosClinicosInvalidosException.class);
    }

    @Test
    void rechazaEdadFueraDeRango() {
        assertThatThrownBy(() -> registrar("Ana María", -1, "Fiebre", 60))
                .isInstanceOfSatisfying(DatosClinicosInvalidosException.class,
                        ex -> assertThat(ex.campo()).isEqualTo("edad"));
        assertThatThrownBy(() -> registrar("Ana María", 121, "Fiebre", 60))
                .isInstanceOf(DatosClinicosInvalidosException.class);
    }

    @Test
    void rechazaQuejaVaciaOExcesivamenteLarga() {
        assertThatThrownBy(() -> registrar("Ana María", 40, "   ", 60))
                .isInstanceOfSatisfying(DatosClinicosInvalidosException.class,
                        ex -> assertThat(ex.campo()).isEqualTo("quejaPrincipal"));
        assertThatThrownBy(() -> registrar("Ana María", 40, "x".repeat(301), 60))
                .isInstanceOf(DatosClinicosInvalidosException.class);
    }

    @Test
    void rechazaSlaNoPositivo() {
        assertThatThrownBy(() -> registrar("Ana María", 40, "Fiebre", 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void elEventoDeRegistroNaceDelPaciente() {
        Paciente p = registrar("Ana María", 40, "Fiebre", 60);

        EventoClinico evento = EventoClinico.registro(p, "cid-1");

        assertThat(evento.tipo()).isEqualTo(TipoEvento.PACIENTE_REGISTRADO);
        assertThat(evento.pacienteId()).isEqualTo(p.id());
        assertThat(evento.nivel()).isEqualTo(p.nivel());
        assertThat(evento.estadoAnterior()).isNull();
        assertThat(evento.estadoNuevo()).isEqualTo(EstadoPaciente.REGISTRADO);
        assertThat(evento.ocurridoEn()).isEqualTo(AHORA);
        assertThat(evento.correlationId()).isEqualTo("cid-1");
        assertThat(evento.eventId()).isNotNull();
    }
}
