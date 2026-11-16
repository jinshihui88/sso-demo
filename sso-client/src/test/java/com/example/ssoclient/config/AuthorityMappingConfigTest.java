package com.example.ssoclient.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 纯单元测试：不启动 Spring 上下文，不依赖 Keycloak。
 */
class AuthorityMappingConfigTest {

    private final GrantedAuthoritiesMapper mapper = new AuthorityMappingConfig().userAuthoritiesMapper();

    @Test
    @DisplayName("realm_access.roles 应转换为 ROLE_ 前缀权限，原权限保留")
    void shouldMapRealmRolesToPrefixedAuthorities() {
        OidcIdToken idToken = idToken(Map.of(
                "sub", "user-1",
                "realm_access", Map.of("roles", List.of("USER", "ADMIN"))
        ));
        Collection<GrantedAuthority> input = List.of(
                new SimpleGrantedAuthority("OIDC_USER"),
                new OidcUserAuthority(idToken)
        );

        Set<String> mapped = mapper.mapAuthorities(input).stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        assertThat(mapped).contains(
                "OIDC_USER",
                "ROLE_USER",
                "ROLE_ADMIN"
        );
    }

    @Test
    @DisplayName("没有 realm_access 声明时不臆造角色")
    void shouldNotInventRolesWhenClaimMissing() {
        OidcIdToken idToken = idToken(Map.of("sub", "user-2"));
        Collection<GrantedAuthority> input = List.of(new OidcUserAuthority(idToken));

        Set<String> mapped = mapper.mapAuthorities(input).stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        assertThat(mapped).noneMatch(authority -> authority.startsWith("ROLE_"));
    }

    @Test
    @DisplayName("空角色列表不会产生 ROLE_ 空权限")
    void shouldIgnoreEmptyRoleNames() {
        OidcIdToken idToken = idToken(Map.of(
                "sub", "user-3",
                "realm_access", Map.of("roles", List.of("", "  "))
        ));
        Collection<GrantedAuthority> input = List.of(new OidcUserAuthority(idToken));

        Set<String> mapped = mapper.mapAuthorities(input).stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        assertThat(mapped).noneMatch(authority -> authority.startsWith("ROLE_"));
    }

    private OidcIdToken idToken(Map<String, Object> claims) {
        Instant now = Instant.now();
        return new OidcIdToken("id-token-value", now, now.plusSeconds(300), claims);
    }
}
