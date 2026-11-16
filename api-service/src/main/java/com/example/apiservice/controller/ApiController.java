package com.example.apiservice.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 受 JWT 保护的 API。
 *
 * <p>调用方必须携带 {@code Authorization: Bearer <Access Token>}，
 * 不要拿 ID Token 当 Access Token 用。</p>
 */
@RestController
@RequestMapping("/api")
public class ApiController {

    @GetMapping("/public")
    public Map<String, String> publicApi() {
        return Map.of("message", "public endpoint");
    }

    @GetMapping("/profile")
    public Map<String, Object> profile(Authentication authentication) {
        // Resource Server 场景下 Authentication 的具体类型是 JwtAuthenticationToken，
        // 它的 principal 是 Jwt；权限挂在 token 上，不在 Jwt 上
        JwtAuthenticationToken authenticationToken = (JwtAuthenticationToken) authentication;
        Jwt jwt = authenticationToken.getToken();
        List<String> authorities = authenticationToken.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .sorted()
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("subject", jwt.getSubject());
        result.put("issuer", jwt.getIssuer().toString());
        result.put("expiresAt", jwt.getExpiresAt() == null ? null : jwt.getExpiresAt().toString());
        result.put("preferredUsername", jwt.getClaimAsString("preferred_username"));
        result.put("authorities", authorities);
        return result;
    }

    @GetMapping("/admin/health")
    public Map<String, String> adminHealth(@AuthenticationPrincipal Jwt jwt) {
        return Map.of(
                "message", "admin access granted",
                "username", String.valueOf(jwt.getClaimAsString("preferred_username"))
        );
    }
}
