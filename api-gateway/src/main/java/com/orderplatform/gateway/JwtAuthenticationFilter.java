package com.orderplatform.gateway;

import com.orderplatform.common.security.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Edge security: validates the JWT, enforces coarse role rules and forwards the caller identity
 * to downstream services as trusted headers (X-User-Name / X-User-Roles).
 * Any client-supplied X-User-* headers are stripped first so they cannot be spoofed.
 */
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    static final String USER_HEADER = "X-User-Name";
    static final String ROLES_HEADER = "X-User-Roles";
    private static final List<String> PUBLIC_PREFIXES = List.of("/api/auth/", "/actuator/");

    private final JwtUtil jwtUtil;

    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        ServerHttpRequest.Builder builder = request.mutate().headers(headers -> {
            headers.remove(USER_HEADER);
            headers.remove(ROLES_HEADER);
        });

        if (PUBLIC_PREFIXES.stream().anyMatch(path::startsWith)) {
            return chain.filter(exchange.mutate().request(builder.build()).build());
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return reject(exchange, HttpStatus.UNAUTHORIZED);
        }

        Claims claims;
        try {
            claims = jwtUtil.parse(authHeader.substring(7));
        } catch (JwtException | IllegalArgumentException ex) {
            return reject(exchange, HttpStatus.UNAUTHORIZED);
        }

        String role = claims.get(JwtUtil.ROLE_CLAIM, String.class);
        boolean writesProducts = path.startsWith("/api/products")
                && !HttpMethod.GET.equals(request.getMethod());
        if (writesProducts && !"ADMIN".equals(role)) {
            return reject(exchange, HttpStatus.FORBIDDEN);
        }

        ServerHttpRequest authenticated = builder
                .header(USER_HEADER, claims.getSubject())
                .header(ROLES_HEADER, role == null ? "" : role)
                .build();
        return chain.filter(exchange.mutate().request(authenticated).build());
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status) {
        exchange.getResponse().setStatusCode(status);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
