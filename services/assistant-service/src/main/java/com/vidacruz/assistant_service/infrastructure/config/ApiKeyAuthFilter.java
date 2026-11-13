package com.vidacruz.assistant_service.infrastructure.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.util.Assert;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class ApiKeyAuthFilter implements WebFilter {

    public static final String HEADER = "X-API-Key";
    private static final String PUBLIC_PREFIX = "/actuator/health";
    private final byte[] expectedKey;

    public ApiKeyAuthFilter(String apiKey) {
        Assert.hasText(apiKey, "assistant.api-key (variable API_KEY) es obligatoria");
        this.expectedKey = apiKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (exchange.getRequest().getPath().value().startsWith(PUBLIC_PREFIX)) {
            return chain.filter(exchange);
        }
        String provided = exchange.getRequest().getHeaders().getFirst(HEADER);
        if (provided != null && MessageDigest.isEqual(provided.getBytes(StandardCharsets.UTF_8), expectedKey)) {
            Authentication auth = new UsernamePasswordAuthenticationToken(
                    "api-client", null, AuthorityUtils.createAuthorityList("ROLE_CLIENT"));
            return chain.filter(exchange).contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth));
        }
        return unauthorized(exchange);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        byte[] body = "{\"status\":401,\"title\":\"No autorizado\",\"detail\":\"API key ausente o inválida\"}"
                .getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }
}
