package com.example.ssoclient.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 已登录但权限不足时的处理器。
 *
 * <p>为什么需要它：{@code /admin/**} 这类 URL 规则在 Spring Security 过滤器链中执行，
 * 早于 DispatcherServlet，因此 {@code @RestControllerAdvice} 抓不到这里的
 * {@link AccessDeniedException}。框架默认的 {@code AccessDeniedHandlerImpl}
 * 会调用 {@code response.sendError(403)}，容器随即把请求转发到 {@code /error}，
 * 项目里没配错误视图时就会渲染 Spring Boot 的 Whitelabel 兜底页。</p>
 *
 * <p>这里改成直接写出 JSON 响应体，不再触发 {@code /error} 转发。
 * 若想给浏览器用户更友好的提示，可判断 {@code Accept} 头后重定向到自定义 403 页面。</p>
 */
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "access_denied");
        body.put("message", "当前用户没有访问该资源的权限");
        body.put("path", request.getRequestURI());
        objectMapper.writeValue(response.getWriter(), body);
    }
}
