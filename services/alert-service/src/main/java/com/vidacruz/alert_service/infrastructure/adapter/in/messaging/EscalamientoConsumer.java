package com.vidacruz.alert_service.infrastructure.adapter.in.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidacruz.alert_service.application.port.in.ProcesarEscalamientoUseCase;
import com.vidacruz.alert_service.application.port.out.DeadLetterPort;
import com.vidacruz.alert_service.domain.model.EventoEscalamiento;
import com.vidacruz.alert_service.domain.model.NivelTriaje;
import com.vidacruz.alert_service.infrastructure.config.AlertProperties;
import com.vidacruz.events_contract.EventEnvelope;
import com.vidacruz.events_contract.EventType;
import com.vidacruz.events_contract.Topics;
import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOptions;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "alert.mensajeria.enabled", havingValue = "true", matchIfMissing = true)
public class EscalamientoConsumer {

    private static final Logger log = LoggerFactory.getLogger(EscalamientoConsumer.class);

    private final ProcesarEscalamientoUseCase procesar;
    private final DeadLetterPort deadLetter;
    private final ObjectMapper objectMapper;
    private final AlertProperties props;
    private Disposable suscripcion;

    public EscalamientoConsumer(ProcesarEscalamientoUseCase procesar, DeadLetterPort deadLetter,
                                ObjectMapper objectMapper, AlertProperties props) {
        this.procesar = procesar;
        this.deadLetter = deadLetter;
        this.objectMapper = objectMapper;
        this.props = props;
    }

    @EventListener(ApplicationReadyEvent.class)
    void start() {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, props.kafka().bootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "alertas-escalamiento",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        ReceiverOptions<String, String> options = ReceiverOptions.<String, String>create(config)
                .subscription(List.of(Topics.TRIAJE_EVENTOS));

        suscripcion = KafkaReceiver.create(options).receive()
                .concatMap(this::procesarRegistro)
                .subscribe();
    }

    @PreDestroy
    void stop() {
        if (suscripcion != null) {
            suscripcion.dispose();
        }
    }

    private Mono<Void> procesarRegistro(ReceiverRecord<String, String> registro) {
        return Mono.fromCallable(() -> objectMapper.readValue(registro.value(), EventEnvelope.class))
                .flatMap(envelope -> procesarSiEscalado(envelope, registro.value()))
                .retryWhen(Retry.backoff(3, Duration.ofMillis(200)))
                .onErrorResume(error -> {
                    log.error("Registro enviado al DLT tras reintentos (offset {}): {}",
                            registro.receiverOffset().offset(), error.toString());
                    return deadLetter.publicar(registro.key(), registro.value());
                })
                .doOnSuccess(ignored -> registro.receiverOffset().acknowledge())
                .onErrorResume(error -> {
                    log.error("Registro sin confirmar; Kafka lo volverá a entregar (offset {}): {}",
                            registro.receiverOffset().offset(), error.toString());
                    return Mono.empty();
                });
    }

    private Mono<Void> procesarSiEscalado(EventEnvelope envelope, String crudo) {
        if (envelope.type() != EventType.PACIENTE_ESCALADO) {
            return Mono.empty();
        }
        return procesar.procesar(new EventoEscalamiento(
                envelope.eventId(),
                envelope.payload().pacienteId(),
                NivelTriaje.desdeCodigo(envelope.payload().nivel()),
                envelope.occurredAt(),
                envelope.payload().slaMinutos(),
                envelope.payload().fechaRegistro(),
                crudo));
    }
}
