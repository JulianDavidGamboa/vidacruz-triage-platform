package com.vidacruz.assistant_service.infrastructure.adapter.in.web;

import com.vidacruz.assistant_service.application.port.in.ConsultarAsistenteUseCase;
import com.vidacruz.assistant_service.application.port.in.ConsultarEstadoUrgenciasUseCase;
import com.vidacruz.assistant_service.domain.model.EstadoUrgencias;
import com.vidacruz.assistant_service.domain.model.Fuente;
import com.vidacruz.assistant_service.domain.model.NivelTriaje;
import com.vidacruz.assistant_service.domain.model.RespuestaAsistente;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/asistente")
public class AsistenteController {

    private final ConsultarAsistenteUseCase consultar;
    private final ConsultarEstadoUrgenciasUseCase estadoUrgencias;

    public AsistenteController(ConsultarAsistenteUseCase consultar, ConsultarEstadoUrgenciasUseCase estadoUrgencias) {
        this.consultar = consultar;
        this.estadoUrgencias = estadoUrgencias;
    }

    @PostMapping("/consulta")
    public Mono<ConsultaResponse> consultar(@Valid @RequestBody ConsultaRequest request) {
        return consultar.consultar(request.pregunta()).map(ConsultaResponse::de);
    }

    @GetMapping("/estado-urgencias")
    public Mono<EstadoUrgenciasResponse> estadoUrgencias() {
        return estadoUrgencias.obtener().map(EstadoUrgenciasResponse::de);
    }

    public record ConsultaRequest(@NotBlank @Size(max = 500) String pregunta) {
    }

    public record ConsultaResponse(String respuesta, List<FuenteResponse> fuentes) {
        static ConsultaResponse de(RespuestaAsistente respuesta) {
            return new ConsultaResponse(respuesta.texto(),
                    respuesta.fuentes().stream().map(FuenteResponse::de).toList());
        }
    }

    public record FuenteResponse(String protocolo, String seccion) {
        static FuenteResponse de(Fuente fuente) {
            return new FuenteResponse(fuente.protocolo(), fuente.seccion());
        }
    }

    public record EstadoUrgenciasResponse(long total, Map<String, Long> porNivel) {
        static EstadoUrgenciasResponse de(EstadoUrgencias estado) {
            Map<String, Long> porNivel = new LinkedHashMap<>();
            for (NivelTriaje nivel : NivelTriaje.values()) {
                porNivel.put(nivel.name(), estado.porNivel().getOrDefault(nivel, 0L));
            }
            return new EstadoUrgenciasResponse(estado.total(), porNivel);
        }
    }
}
