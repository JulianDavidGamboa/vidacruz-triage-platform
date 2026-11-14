package com.vidacruz.alert_service.infrastructure.adapter.out.canal;

import com.vidacruz.alert_service.domain.model.Alerta;
import com.vidacruz.alert_service.domain.model.NivelTriaje;
import com.vidacruz.alert_service.domain.model.ResultadoEnvio;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CanalAlertasCompuestoTest {

    @Test
    void sinCanalesConfiguradosSoloRegistraEnLog() {
        CanalFalso teams = new CanalFalso(false, false);
        CanalFalso discord = new CanalFalso(false, false);

        StepVerifier.create(new CanalAlertasCompuesto(List.of(teams, discord)).enviar(alerta()))
                .expectNext(ResultadoEnvio.SOLO_LOG)
                .verifyComplete();

        assertThat(teams.intentos.get()).isZero();
        assertThat(discord.intentos.get()).isZero();
    }

    @Test
    void siUnoFallaYOtroEntregaEsEnviadaYAmbosSeIntentan() {
        CanalFalso teams = new CanalFalso(true, true);
        CanalFalso discord = new CanalFalso(true, false);

        StepVerifier.create(new CanalAlertasCompuesto(List.of(teams, discord)).enviar(alerta()))
                .expectNext(ResultadoEnvio.ENVIADA)
                .verifyComplete();

        assertThat(teams.intentos.get()).isEqualTo(1);
        assertThat(discord.intentos.get()).isEqualTo(1);
    }

    @Test
    void siTodosLosConfiguradosFallanSeRegistraEnLogDeRespaldo() {
        CanalFalso teams = new CanalFalso(true, true);
        CanalFalso discord = new CanalFalso(true, true);

        StepVerifier.create(new CanalAlertasCompuesto(List.of(teams, discord)).enviar(alerta()))
                .expectNext(ResultadoEnvio.REGISTRADA_EN_LOG)
                .verifyComplete();

        assertThat(teams.intentos.get()).isEqualTo(1);
        assertThat(discord.intentos.get()).isEqualTo(1);
    }

    @Test
    void ignoraLosCanalesSinConfigurar() {
        CanalFalso teams = new CanalFalso(false, true);
        CanalFalso discord = new CanalFalso(true, false);

        StepVerifier.create(new CanalAlertasCompuesto(List.of(teams, discord)).enviar(alerta()))
                .expectNext(ResultadoEnvio.ENVIADA)
                .verifyComplete();

        assertThat(teams.intentos.get()).isZero();
        assertThat(discord.intentos.get()).isEqualTo(1);
    }

    private Alerta alerta() {
        return new Alerta(UUID.randomUUID(), NivelTriaje.URGENTE, Instant.parse("2026-11-12T10:00:00Z"), 30, 42L);
    }

    private static final class CanalFalso implements CanalAlerta {

        final AtomicInteger intentos = new AtomicInteger();
        private final boolean configurado;
        private final boolean falla;

        CanalFalso(boolean configurado, boolean falla) {
            this.configurado = configurado;
            this.falla = falla;
        }

        @Override
        public String nombre() {
            return "falso";
        }

        @Override
        public boolean configurado() {
            return configurado;
        }

        @Override
        public Mono<Void> enviar(Alerta alerta) {
            return Mono.defer(() -> {
                intentos.incrementAndGet();
                return falla ? Mono.error(new IllegalStateException("caido")) : Mono.empty();
            });
        }
    }
}
