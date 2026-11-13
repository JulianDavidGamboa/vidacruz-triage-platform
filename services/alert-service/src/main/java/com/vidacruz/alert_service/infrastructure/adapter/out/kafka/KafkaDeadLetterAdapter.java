package com.vidacruz.alert_service.infrastructure.adapter.out.kafka;

import com.vidacruz.alert_service.application.port.out.DeadLetterPort;
import com.vidacruz.events_contract.Topics;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

@Component
public class KafkaDeadLetterAdapter implements DeadLetterPort {

    private final KafkaSender<String, String> sender;

    public KafkaDeadLetterAdapter(KafkaSender<String, String> sender) {
        this.sender = sender;
    }

    @Override
    public Mono<Void> publicar(String clave, String valor) {
        SenderRecord<String, String, String> registro = SenderRecord.create(
                new ProducerRecord<>(Topics.TRIAJE_EVENTOS_DLT, clave, valor), clave);
        return sender.send(Mono.just(registro))
                .flatMap(resultado -> resultado.exception() == null
                        ? Mono.<Void>empty()
                        : Mono.error(resultado.exception()))
                .then();
    }
}
