package com.vidacruz.triage_service.infrastructure.adapter.out.persistence;

import com.vidacruz.triage_service.application.port.out.PacienteRepositoryPort;
import com.vidacruz.triage_service.domain.model.EstadoPaciente;
import com.vidacruz.triage_service.domain.model.NivelTriaje;
import com.vidacruz.triage_service.domain.model.Paciente;
import com.vidacruz.triage_service.domain.model.SignosVitales;
import io.r2dbc.spi.Readable;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Component
public class PacienteR2dbcAdapter implements PacienteRepositoryPort {

    private static final String INSERTAR = """
            INSERT INTO pacientes (id, nombre, edad, fc, p_sistolica, p_diastolica, spo2, temperatura, dolor, gcs,
                                   queja_principal, nivel, sla_minutos, estado, escalado, fecha_registro,
                                   fecha_estado, fecha_escalado, fecha_primera_consulta)
            VALUES (:id, :nombre, :edad, :fc, :pSistolica, :pDiastolica, :spo2, :temperatura, :dolor, :gcs,
                    :quejaPrincipal, :nivel, :slaMinutos, :estado, :escalado, :fechaRegistro,
                    :fechaEstado, :fechaEscalado, :fechaPrimeraConsulta)
            """;

    private static final String ACTUALIZAR_ESTADO = """
            UPDATE pacientes
               SET estado = :nuevo, fecha_estado = :fechaEstado, fecha_primera_consulta = :fechaPrimeraConsulta
             WHERE id = :id AND estado = :esperado
            """;

    private static final String BUSCAR_POR_ID = """
            SELECT id, nombre, edad, fc, p_sistolica, p_diastolica, spo2, temperatura, dolor, gcs,
                   queja_principal, nivel, sla_minutos, estado, escalado, fecha_registro,
                   fecha_estado, fecha_escalado, fecha_primera_consulta
              FROM pacientes
             WHERE id = :id
            """;

    private final DatabaseClient db;

    public PacienteR2dbcAdapter(DatabaseClient db) {
        this.db = db;
    }

    @Override
    public Mono<Paciente> guardar(Paciente paciente) {
        DatabaseClient.GenericExecuteSpec spec = db.sql(INSERTAR)
                .bind("id", paciente.id())
                .bind("nombre", paciente.nombreCompleto())
                .bind("edad", paciente.edad())
                .bind("fc", paciente.signosVitales().frecuenciaCardiaca())
                .bind("pSistolica", paciente.signosVitales().presionSistolica())
                .bind("pDiastolica", paciente.signosVitales().presionDiastolica())
                .bind("spo2", paciente.signosVitales().spo2())
                .bind("temperatura", paciente.signosVitales().temperatura())
                .bind("dolor", paciente.signosVitales().dolor())
                .bind("gcs", paciente.signosVitales().gcs())
                .bind("quejaPrincipal", paciente.quejaPrincipal())
                .bind("nivel", paciente.nivel().codigo())
                .bind("slaMinutos", paciente.slaMinutos())
                .bind("estado", paciente.estado().name())
                .bind("escalado", paciente.escalado())
                .bind("fechaRegistro", utc(paciente.fechaRegistro()))
                .bind("fechaEstado", utc(paciente.fechaEstado()));
        spec = opcional(spec, "fechaEscalado", paciente.fechaEscalado());
        spec = opcional(spec, "fechaPrimeraConsulta", paciente.fechaPrimeraConsulta());
        return spec.then().thenReturn(paciente);
    }

    @Override
    public Mono<Paciente> buscarPorId(UUID id) {
        return db.sql(BUSCAR_POR_ID)
                .bind("id", id)
                .map(row -> aPaciente(row))
                .one();
    }

    /** Control optimista: 0 filas afectadas significa que otra operación cambió el estado antes. */
    @Override
    public Mono<Boolean> actualizarEstado(Paciente nuevo, EstadoPaciente estadoEsperado) {
        DatabaseClient.GenericExecuteSpec spec = db.sql(ACTUALIZAR_ESTADO)
                .bind("id", nuevo.id())
                .bind("nuevo", nuevo.estado().name())
                .bind("fechaEstado", utc(nuevo.fechaEstado()))
                .bind("esperado", estadoEsperado.name());
        spec = opcional(spec, "fechaPrimeraConsulta", nuevo.fechaPrimeraConsulta());
        return spec.fetch().rowsUpdated().map(filas -> filas > 0);
    }

    private static Paciente aPaciente(Readable row) {
        SignosVitales signos = new SignosVitales(
                row.get("fc", Integer.class),
                row.get("p_sistolica", Integer.class),
                row.get("p_diastolica", Integer.class),
                row.get("spo2", Integer.class),
                row.get("temperatura", Double.class),
                row.get("dolor", Integer.class),
                row.get("gcs", Integer.class));
        return new Paciente(
                row.get("id", UUID.class),
                row.get("nombre", String.class),
                row.get("edad", Integer.class),
                signos,
                row.get("queja_principal", String.class),
                NivelTriaje.desdeCodigo(row.get("nivel", Integer.class)),
                row.get("sla_minutos", Integer.class),
                EstadoPaciente.valueOf(row.get("estado", String.class)),
                row.get("escalado", Boolean.class),
                instante(row, "fecha_registro"),
                instante(row, "fecha_estado"),
                instante(row, "fecha_escalado"),
                instante(row, "fecha_primera_consulta"));
    }

    private static DatabaseClient.GenericExecuteSpec opcional(DatabaseClient.GenericExecuteSpec spec,
                                                              String nombre, Instant valor) {
        return valor == null
                ? spec.bindNull(nombre, OffsetDateTime.class)
                : spec.bind(nombre, utc(valor));
    }

    private static OffsetDateTime utc(Instant instante) {
        return OffsetDateTime.ofInstant(instante, ZoneOffset.UTC);
    }

    private static Instant instante(Readable row, String columna) {
        OffsetDateTime valor = row.get(columna, OffsetDateTime.class);
        return valor == null ? null : valor.toInstant();
    }
}
