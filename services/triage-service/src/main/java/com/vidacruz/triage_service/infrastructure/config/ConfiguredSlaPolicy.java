package com.vidacruz.triage_service.infrastructure.config;

import com.vidacruz.triage_service.application.port.out.SlaPolicy;
import com.vidacruz.triage_service.domain.model.NivelTriaje;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/** RN-02 leída de application.yml. Falla al arrancar si falta algún nivel o el valor no es positivo. */
@Component
public class ConfiguredSlaPolicy implements SlaPolicy {

    private final Map<NivelTriaje, Integer> minutos = new EnumMap<>(NivelTriaje.class);

    public ConfiguredSlaPolicy(TriageProperties props) {
        Map<NivelTriaje, Integer> configurados = props.sla() == null ? Map.of() : props.sla().minutos();
        for (NivelTriaje nivel : NivelTriaje.values()) {
            Integer valor = configurados == null ? null : configurados.get(nivel);
            if (valor == null || valor <= 0) {
                throw new IllegalStateException(
                        "Falta o es inválido triage.sla.minutos." + nivel.name() + " (debe ser > 0)");
            }
            minutos.put(nivel, valor);
        }
    }

    @Override
    public int minutosPara(NivelTriaje nivel) {
        return minutos.get(nivel);
    }
}


