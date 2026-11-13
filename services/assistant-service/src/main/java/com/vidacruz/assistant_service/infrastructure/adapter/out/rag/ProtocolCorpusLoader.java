package com.vidacruz.assistant_service.infrastructure.adapter.out.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class ProtocolCorpusLoader {

    private static final Logger log = LoggerFactory.getLogger(ProtocolCorpusLoader.class);
    private static final Duration TIMEOUT_INTENTO = Duration.ofSeconds(60);
    private static final int REINTENTOS = 6;
    private static final Duration ESPERA_INICIAL = Duration.ofSeconds(5);
    private static final Duration ESPERA_MAXIMA = Duration.ofSeconds(60);

    private final VectorStore vectorStore;
    private final Duration timeoutIntento;
    private final int reintentos;
    private final Duration esperaInicial;
    private final AtomicBoolean listo = new AtomicBoolean(false);

    @Autowired
    public ProtocolCorpusLoader(VectorStore vectorStore) {
        this(vectorStore, TIMEOUT_INTENTO, REINTENTOS, ESPERA_INICIAL);
    }

    ProtocolCorpusLoader(VectorStore vectorStore, Duration timeoutIntento, int reintentos, Duration esperaInicial) {
        this.vectorStore = vectorStore;
        this.timeoutIntento = timeoutIntento;
        this.reintentos = reintentos;
        this.esperaInicial = esperaInicial;
    }

    public boolean estaListo() {
        return listo.get();
    }

    @EventListener(ApplicationReadyEvent.class)
    void cargar() {
        cargarConReintentos().subscribe(
                ignored -> { },
                error -> log.warn("Corpus de protocolos no cargado tras {} reintentos; el asistente responderá 503: {}",
                        reintentos, error.toString()));
    }

    Mono<Void> cargarConReintentos() {
        return Mono.fromCallable(this::documentos)
                .flatMap(docs -> Mono.fromRunnable(() -> vectorStore.add(docs)))
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(timeoutIntento)
                .retryWhen(Retry.backoff(reintentos, esperaInicial)
                        .maxBackoff(ESPERA_MAXIMA)
                        .doBeforeRetry(senal -> log.warn("Carga del corpus fallida (intento {}); reintentando: {}",
                                senal.totalRetries() + 1, senal.failure().toString())))
                .then()
                .doOnSuccess(ignored -> {
                    listo.set(true);
                    log.info("Corpus de protocolos cargado");
                });
    }

    private List<Document> documentos() throws IOException {
        List<Document> documentos = new ArrayList<>();
        var resolver = new PathMatchingResourcePatternResolver();
        for (Resource recurso : resolver.getResources("classpath:protocolos/*.md")) {
            String contenido = recurso.getContentAsString(StandardCharsets.UTF_8);
            int indice = 0;
            for (ProtocoloParser.Fragmento fragmento : ProtocoloParser.parsear(contenido)) {
                documentos.add(Document.builder()
                        .id(recurso.getFilename() + "#" + indice++)
                        .text(fragmento.texto())
                        .metadata(Map.of("protocolo", fragmento.protocolo(), "seccion", fragmento.seccion()))
                        .build());
            }
        }
        return documentos;
    }
}
