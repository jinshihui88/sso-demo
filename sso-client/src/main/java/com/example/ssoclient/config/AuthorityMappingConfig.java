package com.example.ssoclient.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 把 Keycloak 的角色声明转换成 Spring Security 的 Authority。
 *
 * <p>Keycloak 的 Realm Role 默认出现在 {@code realm_access.roles} 声明中，
 * 它不会被 Spring Security 自动识别为 {@code ROLE_ADMIN}。若不转换，
 * {@code hasRole("ADMIN")} 永远不成立，接口会一直 403。</p>
 *
 * <p>前提：Keycloak 客户端上必须配置了把 Realm Role 写入 ID Token 的协议映射
 * （本项目的 realm 导入文件已完成该配置）。</p>
 */
@Configuration
public class AuthorityMappingConfig {

    private static final String REALM_ACCESS = "realm_access";
    private static final String ROLES = "roles";
    private static final String ROLE_PREFIX = "ROLE_";

    @Bean
    public GrantedAuthoritiesMapper userAuthoritiesMapper() {
        return authorities -> {
            Set<GrantedAuthority> mapped = new LinkedHashSet<>(authorities);
            for (GrantedAuthority authority : authorities) {
                if (authority instanceof OidcUserAuthority oidcAuthority) {
                    // ID Token 与 UserInfo 两处都要看：不同 Keycloak 映射配置下角色可能只出现在其中一处
                    addRealmRoles(mapped, oidcAuthority.getIdToken().getClaims());
                    if (oidcAuthority.getUserInfo() != null) {
                        addRealmRoles(mapped, oidcAuthority.getUserInfo().getClaims());
                    }
                }
            }
            return mapped;
        };
    }

    private void addRealmRoles(Set<GrantedAuthority> target, Map<String, Object> claims) {
        if (claims == null) {
            return;
        }
        Object realmAccess = claims.get(REALM_ACCESS);
        if (!(realmAccess instanceof Map<?, ?> realmAccessMap)) {
            return;
        }
        Object roles = realmAccessMap.get(ROLES);
        if (!(roles instanceof Collection<?> roleCollection)) {
            return;
        }
        for (Object role : roleCollection) {
            if (role != null && !role.toString().isBlank()) {
                target.add(new SimpleGrantedAuthority(ROLE_PREFIX + role.toString().toUpperCase()));
            }
        }
    }
}
