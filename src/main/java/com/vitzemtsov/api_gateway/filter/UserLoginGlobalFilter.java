package com.vitzemtsov.api_gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@NullMarked
public class UserLoginGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Сначала удаляем заголовок (на случай подделки), если он был
        ServerHttpRequest sanitizedRequest = exchange.getRequest()
                .mutate()
                .headers(headers -> headers.remove("X-User-Login"))
                .build();
        ServerWebExchange sanitizedExchange = exchange.mutate().request(sanitizedRequest).build();

        // exchange.getPrincipal() всегда содержит аутентификацию, если Security пропустил запрос
        return exchange.getPrincipal()
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class)
                .mapNotNull(jwt -> jwt.getToken().getClaimAsString("preferred_username"))
                .filter(login -> !login.isBlank())
                .map(login -> {
                    // Добавляем заголовок
                    ServerHttpRequest mutatedRequest = sanitizedExchange.getRequest().mutate()
                            .header("X-User-Login", login)
                            .build();
                    return sanitizedExchange.mutate().request(mutatedRequest).build();
                })
                // Если логина нет (например, запрос к публичному эндпоинту) — пропускаем без заголовка
                .switchIfEmpty(Mono.defer(() -> {
                    log.debug(">>> [GATEWAY FILTER] Principal is not JWT or empty. Passing request without header.");
                    return Mono.just(sanitizedExchange);
                }))
                // Вызываем цепочку один раз
                .flatMap(chain::filter);
    }

    @Override
    public int getOrder() {
        return 0;
    }
}