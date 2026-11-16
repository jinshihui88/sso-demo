package com.example.ssoclient.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一处理授权失败，避免方法级鉴权抛出的异常被当成 500 返回。
 *
 * <p>注意：这里只影响控制器内部抛出的异常（如方法级 {@code @PreAuthorize} 校验失败）。
 * URL 规则拦截（{@code /admin/**}）发生在过滤器链中，早于 DispatcherServlet，
 * 由 {@code SecurityConfig} 里配置的 {@code RestAccessDeniedHandler} 负责返回 403 JSON。</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "access_denied");
        body.put("message", "当前用户没有访问该资源的权限");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }
}
