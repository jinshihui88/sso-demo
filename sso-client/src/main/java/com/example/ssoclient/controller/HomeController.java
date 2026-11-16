package com.example.ssoclient.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.stream.Collectors;

/**
 * 首页：给浏览器一个可视化入口，方便手工验证登录 / 登出。
 *
 * <p>登出必须走 POST 并携带 CSRF Token，所以这里把 Token 写进隐藏域，
 * 而不是把 /logout 改成 GET。</p>
 */
@Controller
public class HomeController {

    @Value("${app.instance-name:App}")
    private String instanceName;

    @GetMapping("/")
    @ResponseBody
    public String index(Authentication authentication, CsrfToken csrfToken) {
        String userBlock;
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            userBlock = """
                    <p class="hint">当前未登录。</p>
                    <p><a class="btn" href="/oauth2/authorization/keycloak">使用 Keycloak 登录</a></p>
                    """;
        } else {
            String roles = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .sorted()
                    .collect(Collectors.joining(", "));
            userBlock = """
                    <p>当前用户：<b>%s</b></p>
                    <p>权限：<code>%s</code></p>
                    <ul>
                      <li><a href="/api/me">/api/me</a> 查看 OIDC 用户信息</li>
                      <li><a href="/api/claims">/api/claims</a> 查看全部声明</li>
                      <li><a href="/admin/ping">/admin/ping</a> 需要 ADMIN 角色</li>
                      <li><a href="/admin/report">/admin/report</a> 方法级权限示例</li>
                    </ul>
                    <form method="post" action="/logout">
                      <input type="hidden" name="%s" value="%s"/>
                      <button class="btn danger" type="submit">登出（含单点登出）</button>
                    </form>
                    """.formatted(authentication.getName(), roles,
                    csrfToken.getParameterName(), csrfToken.getToken());
        }

        return """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                  <meta charset="UTF-8"/>
                  <title>%s</title>
                  <style>
                    body { font-family: "Microsoft YaHei UI", system-ui, sans-serif; margin: 48px auto; max-width: 720px;
                           color: #1f2328; line-height: 1.7; }
                    h1 { font-size: 22px; }
                    code { background: #f3f4f6; padding: 1px 5px; border-radius: 3px; }
                    .btn { display: inline-block; padding: 8px 16px; background: #b9201a; color: #fff;
                           text-decoration: none; border-radius: 4px; border: 0; cursor: pointer; font-size: 14px; }
                    .btn.danger { background: #444; }
                    .hint { color: #6b7280; }
                  </style>
                </head>
                <body>
                  <h1>%s 已启动（OIDC SSO Client）</h1>
                  %s
                </body>
                </html>
                """.formatted(instanceName, instanceName, userBlock);
    }
}
