package com.example.ssoclient.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理接口示例。
 *
 * <p>{@code /admin/**} 的访问控制在 {@code SecurityConfig} 的 URL 规则里，
 * {@code /admin/report} 额外演示方法级注解。权限必须在服务端校验，
 * 前端隐藏按钮不能替代服务端鉴权。</p>
 */
@RestController
@RequestMapping("/admin")
public class AdminController {

    @GetMapping("/ping")
    public Map<String, String> ping(Authentication authentication) {
        return Map.of(
                "message", "ADMIN role access succeeded",
                "username", authentication.getName()
        );
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/report")
    public Map<String, String> report() {
        return Map.of("message", "Only ADMIN can access this endpoint");
    }
}
