package com.vidacruz.triage_service.infrastructure.config;

import com.vidacruz.triage_service.application.CorrelationContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Toma X-Correlation-Id de la petición (o genera uno), lo devuelve en la respuesta y lo deja en el
 * Reactor Context para que auditoría y eventos lo registren. Se valida el formato para evitar
 * inyección de líneas en los logs.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdWebFilter implements WebFilter {

    private static final Pattern VALIDO = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String recibido = exchange.getRequest().getHeaders().getFirst(CorrelationContext.HEADER);
        String correlationId = (recibido != null && VALIDO.matcher(recibido).matches())
                ? recibido
                : UUID.randomUUID().toString();

        exchange.getResponse().getHeaders().set(CorrelationContext.HEADER, correlationId);
        return chain.filter(exchange).contextWrite(ctx -> ctx.put(CorrelationContext.KEY, correlationId));
    }
}

