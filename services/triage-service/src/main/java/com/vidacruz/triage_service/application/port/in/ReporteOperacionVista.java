package com.vidacruz.triage_service.application.port.in;

import java.time.Instant;
import java.util.List;

public record ReporteOperacionVista(Instant desde, Instant hasta, List<FranjaHorariaVista> franjas) {
}
