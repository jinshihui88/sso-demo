package com.example.apiservice.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 纯单元测试：直接构造 Jwt 对象，验证 scope 与 realm_access.roles 的权限转换。
 */
class JwtAuthorityConfigTest {

    private final JwtAuthenticationConverter converter =
            new JwtAuthorityConfig().jwtAuthenticationConverter();

    @Test
    @DisplayName("scope 与 realm_access.roles 都应转为权限")
    void shouldMapScopesAndRealmRoles() {
        Jwt jwt = jwt(Map.of(
                "scope", "openid profile email",
                "realm_access", Map.of("roles", List.of("USER", "ADMIN"))
        ));

        Set<String> authorities = converter.convert(jwt).getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        assertThat(authorities).contains(
                "SCOPE_openid",
                "SCOPE_profile",
                "ROLE_USER",
                "ROLE_ADMIN"
        );
    }

    @Test
    @DisplayName("只有 scope 时不应臆造角色")
    void shouldNotInventRolesWithoutRealmAccess() {
        Jwt jwt = jwt(Map.of("scope", "openid"));

        Set<String> authorities = converter.convert(jwt).getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        assertThat(authorities).contains("SCOPE_openid");
        assertThat(authorities).noneMatch(authority -> authority.startsWith("ROLE_"));
    }

    @Test
    @DisplayName("realm_access 结构异常时不抛异常")
    void shouldTolerateMalformedRealmAccess() {
        Jwt jwt = jwt(Map.of("realm_access", "not-a-map"));

        Collection<GrantedAuthority> authorities = converter.convert(jwt).getAuthorities();

        assertThat(authorities).isEmpty();
    }

    private Jwt jwt(Map<String, Object> claims) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("access-token-value")
                .header("alg", "RS256")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claims(c -> c.putAll(claims))
                .build();
    }
}
