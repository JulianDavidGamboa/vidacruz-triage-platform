package com.vidacruz.triage_service.application;

import com.vidacruz.triage_service.application.port.in.PacienteVista;
import com.vidacruz.triage_service.application.port.out.PacienteRepositoryPort;
import com.vidacruz.triage_service.application.service.ConsultarPacienteService;
import com.vidacruz.triage_service.domain.exception.PacienteNoEncontradoException;
import com.vidacruz.triage_service.domain.model.EstadoPaciente;
import com.vidacruz.triage_service.domain.model.NivelTriaje;
import com.vidacruz.triage_service.domain.model.Paciente;
import com.vidacruz.triage_service.domain.model.SignosVitales;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsultarPacienteServiceTest {

    @Mock PacienteRepositoryPort pacientes;

    @Test
    void calculaMinutosEnElEstadoActualConElRelojDelMomento() {
        Instant entrada = Instant.parse("2026-10-29T14:03:00Z");
        Instant ahora = entrada.plusSeconds(15 * 60);
        Paciente enConsulta = Paciente.registrar(UUID.randomUUID(), "Carlos Gómez", 55,
                        new SignosVitales(130, 180, 110, 97, 36.8, 8, 15), "Dolor torácico",
                        NivelTriaje.CRITICO, 5, entrada.minusSeconds(180))
                .cambiarEstado(EstadoPaciente.EN_CONSULTA, entrada);
        when(pacientes.buscarPorId(enConsulta.id())).thenReturn(Mono.just(enConsulta));

        ConsultarPacienteService service = new ConsultarPacienteService(pacientes, () -> ahora);

        StepVerifier.create(service.obtener(enConsulta.id()))
                .assertNext((PacienteVista v) -> {
                    assertThat(v.paciente().estado()).isEqualTo(EstadoPaciente.EN_CONSULTA);
                    assertThat(v.minutosEnEstadoActual()).isEqualTo(15);
                })
                .verifyComplete();
    }

    @Test
    void pacienteInexistenteDevuelveNotFound() {
        UUID id = UUID.randomUUID();
        when(pacientes.buscarPorId(id)).thenReturn(Mono.empty());

        ConsultarPacienteService service = new ConsultarPacienteService(pacientes, Instant::now);

        StepVerifier.create(service.obtener(id))
                .expectError(PacienteNoEncontradoException.class)
                .verify();
    }
}
