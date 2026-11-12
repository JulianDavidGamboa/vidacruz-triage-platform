package com.vidacruz.assistant_service.infrastructure.adapter.out.llm;

import com.vidacruz.assistant_service.application.port.out.LlmPort;
import com.vidacruz.assistant_service.domain.exception.AsistenteNoDisponibleException;
import com.vidacruz.assistant_service.domain.model.FragmentoProtocolo;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

/** Chat con Spring AI. Temperatura 0 y prompt que obliga a citar el contexto (RN-05). */
@Component
public class SpringAiLlmAdapter implements LlmPort {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String INSTRUCCIONES = """
            Eres un asistente de apoyo clínico. Responde únicamente con la información de los protocolos \
            proporcionados. Cita cada afirmación con el identificador del protocolo entre corchetes, por ejemplo [PROTO-001]. \
            Si los protocolos no responden la pregunta, dilo explícitamente y no añadas contenido clínico.""";

    private final ChatClient chatClient;
    private final CircuitBreaker llm;
    private final Timer latencia;

    public SpringAiLlmAdapter(ChatModel chatModel, @Qualifier("llmCircuitBreaker") CircuitBreaker llm,
                              MeterRegistry registry) {
        this.chatClient = ChatClient.builder(chatModel).build();
        this.llm = llm;
        this.latencia = registry.timer("asistente.llm.latencia");
    }

    @Override
    public Mono<String> responder(String pregunta, List<FragmentoProtocolo> contexto) {
        String protocolos = contexto.stream()
                .map(f -> "[%s] %s: %s".formatted(f.protocolo(), f.seccion(), f.texto()))
                .collect(Collectors.joining("\n"));
        String mensaje = "Protocolos:\n" + protocolos + "\n\nPregunta: " + pregunta;

        return Mono.defer(() -> {
            long inicio = System.nanoTime();
            return Mono.fromCallable(() -> chatClient.prompt()
                            .system(INSTRUCCIONES)
                            .user(mensaje)
                            .options(ChatOptions.builder().temperature(0.0).build())
                            .call()
                            .content())
                    .subscribeOn(Schedulers.boundedElastic())
                    .transformDeferred(CircuitBreakerOperator.of(llm))
                    .timeout(TIMEOUT)
                    .switchIfEmpty(Mono.error(new AsistenteNoDisponibleException("El proveedor de IA no devolvió contenido")))
                    .onErrorMap(error -> error instanceof AsistenteNoDisponibleException
                            ? error
                            : new AsistenteNoDisponibleException("El proveedor de IA no está disponible", error))
                    .doFinally(señal -> latencia.record(Duration.ofNanos(System.nanoTime() - inicio)));
        });
    }
}
