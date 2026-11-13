package com.vidacruz.assistant_service.application.port.out;

import com.vidacruz.assistant_service.domain.model.FragmentoProtocolo;
import reactor.core.publisher.Flux;

public interface ProtocolRetrieverPort {
    Flux<FragmentoProtocolo> recuperar(String pregunta);
}
