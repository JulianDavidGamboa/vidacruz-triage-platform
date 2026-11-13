package com.vidacruz.assistant_service.domain.model;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public record EstadoUrgencias(Map<NivelTriaje, Long> porNivel) {

    public EstadoUrgencias {
        porNivel = Map.copyOf(porNivel);
    }

    public static EstadoUrgencias de(List<NivelTriaje> activos) {
        Map<NivelTriaje, Long> conteo = new EnumMap<>(NivelTriaje.class);
        for (NivelTriaje nivel : NivelTriaje.values()) {
            conteo.put(nivel, 0L);
        }
        activos.forEach(nivel -> conteo.merge(nivel, 1L, Long::sum));
        return new EstadoUrgencias(conteo);
    }

    public long total() {
        return porNivel.values().stream().mapToLong(Long::longValue).sum();
    }
}
