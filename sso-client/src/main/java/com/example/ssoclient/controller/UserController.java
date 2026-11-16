package com.example.ssoclient.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户信息接口：读取 OIDC 身份，并展示实际生效的权限。
 *
 * <p>注意一个容易踩的坑：{@code OidcUser#getAuthorities()} 返回的是 OIDC 服务
 * 生成的原始权限（OIDC_USER、SCOPE_*），而经过 {@code GrantedAuthoritiesMapper}
 * 转换后的权限挂在 {@code Authentication} 上。判断用户能访问什么，
 * 要看 {@code Authentication#getAuthorities()}。</p>
 */
@RestController
@RequestMapping("/api")
public class UserController {

    /**
     * 返回当前 OIDC 用户的核心身份信息。
     *
     * <p>用户唯一标识使用 {@code sub}，不要用 email 或姓名作永久唯一键：
     * 它们都可能变化。</p>
     */
    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal OidcUser oidcUser,
                                  Authentication authentication) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("subject", oidcUser.getSubject());
        result.put("username", oidcUser.getPreferredUsername());
        result.put("name", oidcUser.getFullName());
        result.put("email", oidcUser.getEmail());
        result.put("issuer", oidcUser.getIssuer().toString());
        result.put("authorities", effectiveAuthorities(authentication));
        return result;
    }

    /**
     * 返回 ID Token 的全部声明，便于在 Keycloak 侧调整映射后立即查看效果。
     */
    @GetMapping("/claims")
    public Map<String, Object> claims(@AuthenticationPrincipal OidcUser oidcUser,
                                      Authentication authentication) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("idTokenClaims", oidcUser.getIdToken().getClaims());
        result.put("effectiveAuthorities", effectiveAuthorities(authentication));
        result.put("oidcUserAuthorities", oidcUser.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .sorted()
                .collect(Collectors.toList()));
        return result;
    }

    /**
     * 一个普通受保护接口，用于确认本地 Session 已生效。
     */
    @GetMapping("/hello")
    public Map<String, String> hello(Authentication authentication) {
        return Map.of(
                "username", authentication.getName(),
                "message", "OIDC SSO authentication succeeded"
        );
    }

    private List<String> effectiveAuthorities(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .sorted()
                .collect(Collectors.toList());
    }
}
