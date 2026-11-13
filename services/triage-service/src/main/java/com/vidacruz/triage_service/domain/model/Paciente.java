package com.vidacruz.triage_service.domain.model;

import com.vidacruz.triage_service.domain.exception.TransicionInvalidaException;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;


public record Paciente(
        UUID id,
        String nombreCompleto,
        int edad,
        SignosVitales signosVitales,
        String quejaPrincipal,
        NivelTriaje nivel,
        int slaMinutos,
        EstadoPaciente estado,
        boolean escalado,
        Instant fechaRegistro,
        Instant fechaEstado,
        Instant fechaEscalado,
        Instant fechaPrimeraConsulta) {

    /** Fábrica del caso "paciente recién llegado": estado REGISTRADO, sin escalar y sin consulta. */
    public static Paciente registrar(UUID id, String nombreCompleto, int edad, SignosVitales signos,
                                     String quejaPrincipal, NivelTriaje nivel, int slaMinutos, Instant ahora) {
        Objects.requireNonNull(id, "id requerido");
        Objects.requireNonNull(signos, "signos vitales requeridos");
        Objects.requireNonNull(nivel, "nivel requerido");
        Objects.requireNonNull(ahora, "fecha de registro requerida");

        String nombre = nombreCompleto == null ? "" : nombreCompleto.trim();
        String queja = quejaPrincipal == null ? "" : quejaPrincipal.trim();
        Validacion.longitud("nombreCompleto", nombre, 3, 100);
        Validacion.entre("edad", edad, 0, 120, "años");
        Validacion.obligatorioConMaximo("quejaPrincipal", queja, 300);
        if (slaMinutos <= 0) {
            throw new IllegalArgumentException("slaMinutos debe ser positivo");
        }

        return new Paciente(id, nombre, edad, signos, queja, nivel, slaMinutos,
                EstadoPaciente.REGISTRADO, false, ahora, ahora, null, null);
    }

    /**
     * Aplica una transición válida y devuelve un nuevo paciente (inmutable).
     * La primera entrada a consulta se fija una sola vez; es la base del tiempo de espera del reporte.
     */
    public Paciente cambiarEstado(EstadoPaciente destino, Instant ahora) {
        Objects.requireNonNull(destino, "estado destino requerido");
        Objects.requireNonNull(ahora, "instante requerido");
        if (!estado.puedeTransicionarA(destino)) {
            throw new TransicionInvalidaException(estado, destino);
        }
        Instant primeraConsulta = (destino == EstadoPaciente.EN_CONSULTA && fechaPrimeraConsulta == null)
                ? ahora
                : fechaPrimeraConsulta;
        return new Paciente(id, nombreCompleto, edad, signosVitales, quejaPrincipal, nivel, slaMinutos,
                destino, escalado, fechaRegistro, ahora, fechaEscalado, primeraConsulta);
    }

    /**
     * Regla de escalamiento. Es la referencia que implementa el UPDATE de
     * EscalamientoR2dbcAdapter; el test de integración verifica que ambos coinciden en el límite.
     * Solo aplica a pacientes en REGISTRADO, sin escalar, cuyo SLA venció estrictamente antes de {@code ahora}.
     */
    public boolean superaSla(Instant ahora) {
        if (estado != EstadoPaciente.REGISTRADO || escalado) {
            return false;
        }
        return ahora.isAfter(fechaRegistro.plus(Duration.ofMinutes(slaMinutos)));
    }

    /** Tiempo en el estado actual, en minutos completos (nunca negativo). */
    public long minutosEnEstadoActual(Instant ahora) {
        return Math.max(0, Duration.between(fechaEstado, ahora).toMinutes());
    }
}
