package com.vitzemtsov.api_gateway.config;

import com.vitzemtsov.api_gateway.security.KeycloakRealmRoleConverter;
import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcReactiveOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.ReactiveOAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/actuator/health", "/actuator/info", "/actuator/gateway/**").permitAll()
                        .pathMatchers("/api/v1/files/**").hasAnyRole("USER", "ADMIN")
                        .anyExchange().authenticated()
                )
                .oauth2Login(Customizer.withDefaults())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(reactiveJwtAuthenticationConverter())
                        )
                );

        return http.build();
    }

    @Bean
    public ReactiveOAuth2UserService<@NonNull OidcUserRequest, @NonNull OidcUser> oidcUserService() {
        OidcReactiveOAuth2UserService delegate = new OidcReactiveOAuth2UserService();

        return userRequest -> delegate.loadUser(userRequest)
                .map(user -> {
                    List<GrantedAuthority> allAuthorities = new ArrayList<>(user.getAuthorities());

                    Map<String, Object> realmAccess = user.getIdToken().getClaim("realm_access");
                    if (realmAccess != null) {
                        Object rolesObj = realmAccess.get("roles");
                        if (rolesObj instanceof Collection<?> roles) {
                            List<GrantedAuthority> rolesAuthorities = roles.stream()
                                    .filter(String.class::isInstance)
                                    .map(String.class::cast)
                                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase(Locale.ROOT)))
                                    .collect(Collectors.toList());
                            allAuthorities.addAll(rolesAuthorities);
                        }
                    }

                    return new DefaultOidcUser(allAuthorities, user.getIdToken(), user.getUserInfo());
                });
    }

    @Bean
    public ReactiveJwtAuthenticationConverter reactiveJwtAuthenticationConverter() {
        ReactiveJwtAuthenticationConverter converter = new ReactiveJwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
        return converter;
    }
}