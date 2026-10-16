package com.vitzemtsov.api_gateway.security;

import org.jspecify.annotations.NonNull;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import reactor.core.publisher.Flux;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;

public class KeycloakRealmRoleConverter implements Converter<@NonNull Jwt, Flux<@NonNull GrantedAuthority>> {

    @Override
    @SuppressWarnings("unchecked")
    public Flux<@NonNull GrantedAuthority> convert(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess == null) {
            return Flux.empty();
        }
        Object rolesObj = realmAccess.get("roles");
        if (!(rolesObj instanceof Collection<?> roles)) {
            return Flux.empty();
        }
        return Flux.fromIterable((Collection<String>) roles)
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase(Locale.ROOT)));
    }
}