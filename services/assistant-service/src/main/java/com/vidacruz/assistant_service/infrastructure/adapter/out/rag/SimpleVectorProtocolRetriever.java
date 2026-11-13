package com.vidacruz.assistant_service.infrastructure.adapter.out.rag;

import com.vidacruz.assistant_service.application.port.out.ProtocolRetrieverPort;
import com.vidacruz.assistant_service.domain.exception.AsistenteNoDisponibleException;
import com.vidacruz.assistant_service.domain.model.FragmentoProtocolo;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;

@Component
public class SimpleVectorProtocolRetriever implements ProtocolRetrieverPort {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final VectorStore vectorStore;
    private final ProtocolCorpusLoader corpus;
    private final CircuitBreaker embeddings;
    private final int topK;

    public SimpleVectorProtocolRetriever(VectorStore vectorStore, ProtocolCorpusLoader corpus,
                                         @Qualifier("embeddingsCircuitBreaker") CircuitBreaker embeddings,
                                         @Value("${assistant.rag.top-k}") int topK) {
        this.vectorStore = vectorStore;
        this.corpus = corpus;
        this.embeddings = embeddings;
        this.topK = topK;
    }

    @Override
    public Flux<FragmentoProtocolo> recuperar(String pregunta) {
        if (!corpus.estaListo()) {
            return Flux.error(new AsistenteNoDisponibleException("El corpus de protocolos aún no está cargado"));
        }
        return Mono.fromCallable(() -> vectorStore.similaritySearch(
                        SearchRequest.builder().query(pregunta).topK(topK).build()))
                .subscribeOn(Schedulers.boundedElastic())
                .transformDeferred(CircuitBreakerOperator.of(embeddings))
                .timeout(TIMEOUT)
                .onErrorMap(error -> new AsistenteNoDisponibleException(
                        "No se pudo consultar el corpus de protocolos", error))
                .flatMapIterable(documentos -> documentos.stream().map(SimpleVectorProtocolRetriever::aFragmento).toList());
    }

    private static FragmentoProtocolo aFragmento(Document documento) {
        double similitud = documento.getScore() == null ? 0.0 : documento.getScore();
        return new FragmentoProtocolo(
                String.valueOf(documento.getMetadata().get("protocolo")),
                String.valueOf(documento.getMetadata().get("seccion")),
                documento.getText(),
                similitud);
    }
}
