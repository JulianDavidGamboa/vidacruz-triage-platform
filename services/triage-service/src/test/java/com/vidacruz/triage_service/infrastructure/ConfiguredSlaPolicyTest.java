package com.vidacruz.triage_service.infrastructure;

import com.vidacruz.triage_service.domain.model.NivelTriaje;
import com.vidacruz.triage_service.infrastructure.config.ConfiguredSlaPolicy;
import com.vidacruz.triage_service.infrastructure.config.TriageProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Los SLA vienen de la configuración y deben estar completos y ser positivos. */
class ConfiguredSlaPolicyTest {

    private static TriageProperties props(Map<NivelTriaje, Integer> minutos) {
        return new TriageProperties("clave",
                new TriageProperties.Sla(minutos, Duration.ofSeconds(30), true),
                new TriageProperties.Kafka("localhost:9092", "triaje.eventos"),
                new TriageProperties.Outbox(Duration.ofMillis(500), 100));
    }

    private static Map<NivelTriaje, Integer> completo() {
        Map<NivelTriaje, Integer> m = new EnumMap<>(NivelTriaje.class);
        m.put(NivelTriaje.CRITICO, 5);
        m.put(NivelTriaje.URGENTE, 10);
        m.put(NivelTriaje.URGENTE_2, 30);
        m.put(NivelTriaje.NO_URGENTE, 60);
        m.put(NivelTriaje.NO_URGENTE_2, 120);
        return m;
    }

    @Test
    void devuelveLosMinutosConfiguradosParaCadaNivel() {
        ConfiguredSlaPolicy policy = new ConfiguredSlaPolicy(props(completo()));

        assertThat(policy.minutosPara(NivelTriaje.CRITICO)).isEqualTo(5);
        assertThat(policy.minutosPara(NivelTriaje.URGENTE)).isEqualTo(10);
        assertThat(policy.minutosPara(NivelTriaje.URGENTE_2)).isEqualTo(30);
        assertThat(policy.minutosPara(NivelTriaje.NO_URGENTE)).isEqualTo(60);
        assertThat(policy.minutosPara(NivelTriaje.NO_URGENTE_2)).isEqualTo(120);
    }

    @Test
    void permiteReducirLosSlaParaProbarElEscalamiento() {
        Map<NivelTriaje, Integer> rapido = completo();
        rapido.put(NivelTriaje.CRITICO, 1);

        assertThat(new ConfiguredSlaPolicy(props(rapido)).minutosPara(NivelTriaje.CRITICO)).isEqualTo(1);
    }

    @Test
    void fallaAlArrancarSiFaltaUnNivel() {
        Map<NivelTriaje, Integer> incompleto = completo();
        incompleto.remove(NivelTriaje.URGENTE_2);

        assertThatThrownBy(() -> new ConfiguredSlaPolicy(props(incompleto)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("URGENTE_2");
    }

    @Test
    void fallaAlArrancarSiUnSlaNoEsPositivo() {
        Map<NivelTriaje, Integer> invalido = completo();
        invalido.put(NivelTriaje.CRITICO, 0);

        assertThatThrownBy(() -> new ConfiguredSlaPolicy(props(invalido)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CRITICO");
    }

    @Test
    void fallaAlArrancarSiNoHayConfiguracionDeSla() {
        TriageProperties sinSla = new TriageProperties("clave", null,
                new TriageProperties.Kafka("localhost:9092", "triaje.eventos"),
                new TriageProperties.Outbox(Duration.ofMillis(500), 100));

        assertThatThrownBy(() -> new ConfiguredSlaPolicy(sinSla)).isInstanceOf(IllegalStateException.class);
    }
}
