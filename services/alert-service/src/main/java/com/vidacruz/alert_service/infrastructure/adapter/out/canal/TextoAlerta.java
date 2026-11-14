package com.vidacruz.alert_service.infrastructure.adapter.out.canal;

import com.vidacruz.alert_service.domain.model.Alerta;

final class TextoAlerta {

    private TextoAlerta() {
    }

    static String de(Alerta alerta) {
        String base = "Escalamiento de paciente %s: nivel %s (código %d)"
                .formatted(alerta.pacienteId(), alerta.nivel().name(), alerta.nivel().codigo());
        if (alerta.slaMinutos() != null && alerta.minutosExcedidos() != null) {
            base += ", SLA %d min, excedido %d min".formatted(alerta.slaMinutos(), alerta.minutosExcedidos());
        }
        return base + " desde %s. Consulte el detalle en la API de triaje.".formatted(alerta.ocurridoEn());
    }
}
