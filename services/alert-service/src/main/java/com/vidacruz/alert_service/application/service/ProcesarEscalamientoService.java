package com.vidacruz.alert_service.application.service;

import com.vidacruz.alert_service.application.port.in.ProcesarEscalamientoUseCase;
import com.vidacruz.alert_service.application.port.out.AlertChannelPort;
import com.vidacruz.alert_service.application.port.out.DeadLetterPort;
import com.vidacruz.alert_service.domain.model.Alerta;
import com.vidacruz.alert_service.domain.model.EventoEscalamiento;
import com.vidacruz.alert_service.domain.model.EventosRecientes;
import com.vidacruz.alert_service.domain.model.ResultadoEnvio;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class ProcesarEscalamientoService implements ProcesarEscalamientoUseCase {

    private final AlertChannelPort canal;
    private final DeadLetterPort deadLetter;
    private final EventosRecientes recientes;

    public ProcesarEscalamientoService(AlertChannelPort canal, DeadLetterPort deadLetter, EventosRecientes recientes) {
        this.canal = canal;
        this.deadLetter = deadLetter;
        this.recientes = recientes;
    }

    @Override
    public Mono<Void> procesar(EventoEscalamiento evento) {
        if (recientes.contiene(evento.eventId())) {
            return Mono.empty();
        }
        Alerta alerta = new Alerta(evento.pacienteId(), evento.nivel(), evento.ocurridoEn(),
                evento.slaMinutos(), evento.minutosExcedidos());
        return canal.enviar(alerta)
                .flatMap(resultado -> resultado == ResultadoEnvio.REGISTRADA_EN_LOG
                        ? deadLetter.publicar(evento.pacienteId().toString(), evento.crudo())
                        : Mono.<Void>empty())
                .then(Mono.fromRunnable(() -> recientes.registrar(evento.eventId())));
    }
}
