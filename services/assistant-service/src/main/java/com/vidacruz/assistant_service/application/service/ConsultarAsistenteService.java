package com.vidacruz.assistant_service.application.service;

import com.vidacruz.assistant_service.application.port.in.ConsultarAsistenteUseCase;
import com.vidacruz.assistant_service.application.port.out.LlmPort;
import com.vidacruz.assistant_service.application.port.out.ProtocolRetrieverPort;
import com.vidacruz.assistant_service.domain.model.Fuente;
import com.vidacruz.assistant_service.domain.model.FragmentoProtocolo;
import com.vidacruz.assistant_service.domain.model.RespuestaAsistente;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ConsultarAsistenteService implements ConsultarAsistenteUseCase {

    private static final Pattern CITA = Pattern.compile("PROTO-\\d{3}");

    private final ProtocolRetrieverPort retriever;
    private final LlmPort llm;
    private final double umbralSimilitud;

    public ConsultarAsistenteService(ProtocolRetrieverPort retriever, LlmPort llm,
                                     @Value("${assistant.rag.similarity-threshold}") double umbralSimilitud) {
        this.retriever = retriever;
        this.llm = llm;
        this.umbralSimilitud = umbralSimilitud;
    }

    @Override
    public Mono<RespuestaAsistente> consultar(String pregunta) {
        return retriever.recuperar(pregunta)
                .filter(fragmento -> fragmento.similitud() >= umbralSimilitud)
                .collectList()
                .flatMap(fragmentos -> fragmentos.isEmpty()
                        ? Mono.just(RespuestaAsistente.sinProtocolo())
                        : llm.responder(pregunta, fragmentos).map(texto -> respuesta(texto, fragmentos)));
    }

    private RespuestaAsistente respuesta(String texto, List<FragmentoProtocolo> fragmentos) {
        Set<String> recuperados = fragmentos.stream()
                .map(FragmentoProtocolo::protocolo)
                .collect(Collectors.toSet());
        boolean citaAjeno = CITA.matcher(texto).results()
                .map(MatchResult::group)
                .anyMatch(cita -> !recuperados.contains(cita));
        if (texto.isBlank() || citaAjeno) {
            return RespuestaAsistente.sinProtocolo();
        }
        List<Fuente> fuentes = fragmentos.stream()
                .map(fragmento -> new Fuente(fragmento.protocolo(), fragmento.seccion()))
                .distinct()
                .toList();
        return new RespuestaAsistente(texto, fuentes);
    }
}
