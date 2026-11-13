package com.vidacruz.assistant_service.infrastructure.adapter.out.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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
    private static final Duration TIMEOUT_CARGA = Duration.ofSeconds(60);

    private final VectorStore vectorStore;
    private final AtomicBoolean listo = new AtomicBoolean(false);

    public ProtocolCorpusLoader(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public boolean estaListo() {
        return listo.get();
    }

    @EventListener(ApplicationReadyEvent.class)
    void cargar() {
        Mono.fromCallable(this::documentos)
                .flatMap(docs -> Mono.fromRunnable(() -> vectorStore.add(docs)))
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(TIMEOUT_CARGA)
                .subscribe(
                        ignored -> { },
                        error -> log.warn("Corpus de protocolos no cargado; el asistente responderá 503: {}", error.toString()),
                        () -> {
                            listo.set(true);
                            log.info("Corpus de protocolos cargado");
                        });
    }

    private List<Document> documentos() throws IOException {
        List<Document> documentos = new ArrayList<>();
        var resolver = new PathMatchingResourcePatternResolver();
        for (Resource recurso : resolver.getResources("classpath:protocolos/*.md")) {
            String contenido = recurso.getContentAsString(StandardCharsets.UTF_8);
            for (ProtocoloParser.Fragmento fragmento : ProtocoloParser.parsear(contenido)) {
                documentos.add(Document.builder()
                        .text(fragmento.texto())
                        .metadata(Map.of("protocolo", fragmento.protocolo(), "seccion", fragmento.seccion()))
                        .build());
            }
        }
        return documentos;
    }
}
