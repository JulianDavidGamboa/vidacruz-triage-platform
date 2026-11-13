package com.vidacruz.assistant_service.infrastructure.adapter.out.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ProtocolCorpusLoaderTest {

    private static final int REINTENTOS = 2;
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final Duration ESPERA = Duration.ofMillis(10);

    private final VectorStore vectorStore = mock(VectorStore.class);
    private final ProtocolCorpusLoader loader = new ProtocolCorpusLoader(vectorStore, TIMEOUT, REINTENTOS, ESPERA);

    @Test
    void quedaListoCuandoLaPrimeraCargaTieneExito() {
        doNothing().when(vectorStore).add(anyList());

        StepVerifier.create(loader.cargarConReintentos()).verifyComplete();

        assertThat(loader.estaListo()).isTrue();
        verify(vectorStore, times(1)).add(anyList());
    }

    @Test
    void reintentaYQuedaListoSiLosPrimerosIntentosFallan() {
        AtomicInteger llamadas = new AtomicInteger();
        doAnswer(invocacion -> {
            if (llamadas.incrementAndGet() <= REINTENTOS) {
                throw new IllegalStateException("proveedor no disponible");
            }
            return null;
        }).when(vectorStore).add(anyList());

        StepVerifier.create(loader.cargarConReintentos()).verifyComplete();

        assertThat(loader.estaListo()).isTrue();
        verify(vectorStore, times(REINTENTOS + 1)).add(anyList());
    }

    @Test
    void siAgotaLosReintentosNoQuedaListo() {
        doAnswer(invocacion -> {
            throw new IllegalStateException("proveedor no disponible");
        }).when(vectorStore).add(anyList());

        StepVerifier.create(loader.cargarConReintentos()).expectError().verify();

        assertThat(loader.estaListo()).isFalse();
        verify(vectorStore, times(REINTENTOS + 1)).add(anyList());
    }

    @Test
    void cadaReintentoUsaLosMismosIdentificadoresDeDocumento() {
        List<List<String>> idsPorIntento = new java.util.ArrayList<>();
        AtomicInteger llamadas = new AtomicInteger();
        doAnswer(invocacion -> {
            List<Document> docs = invocacion.getArgument(0);
            idsPorIntento.add(docs.stream().map(Document::getId).toList());
            if (llamadas.incrementAndGet() == 1) {
                throw new IllegalStateException("proveedor no disponible");
            }
            return null;
        }).when(vectorStore).add(anyList());

        StepVerifier.create(loader.cargarConReintentos()).verifyComplete();

        assertThat(idsPorIntento).hasSize(2);
        assertThat(idsPorIntento.get(0)).isNotEmpty().isEqualTo(idsPorIntento.get(1));
    }
}
