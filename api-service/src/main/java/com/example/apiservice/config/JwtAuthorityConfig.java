package com.example.apiservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 把 Access Token 中的声明转换成 Spring Security 权限。
 *
 * <p>Resource Server 默认只把 {@code scope}/{@code scp} 映射为 {@code SCOPE_} 前缀权限，
 * 不会把 Keycloak 的 {@code realm_access.roles} 变成 {@code ROLE_ADMIN}。
 * 因此 {@code hasRole("ADMIN")} 需要这里显式转换。</p>
 */
@Configuration
public class JwtAuthorityConfig {

    private static final String REALM_ACCESS = "realm_access";
    private static final String ROLES = "roles";
    private static final String ROLE_PREFIX = "ROLE_";

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter scopeConverter = new JwtGrantedAuthoritiesConverter();

        Converter<Jwt, Collection<GrantedAuthority>> authoritiesConverter = jwt -> {
            Set<GrantedAuthority> authorities = new LinkedHashSet<>();
            Collection<GrantedAuthority> scopes = scopeConverter.convert(jwt);
            if (scopes != null) {
                authorities.addAll(scopes);
            }
            Object realmAccess = jwt.getClaim(REALM_ACCESS);
            if (realmAccess instanceof Map<?, ?> realmAccessMap) {
                Object roles = realmAccessMap.get(ROLES);
                if (roles instanceof Collection<?> roleCollection) {
                    for (Object role : roleCollection) {
                        if (role != null && !role.toString().isBlank()) {
                            authorities.add(new SimpleGrantedAuthority(
                                    ROLE_PREFIX + role.toString().toUpperCase()));
                        }
                    }
                }
            }
            return new ArrayList<>(authorities);
        };

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }
}
