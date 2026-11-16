package com.example.ssoclient.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;

/**
 * 业务客户端的 Spring Security 配置。
 *
 * <p>两个关键点：</p>
 * <ol>
 *   <li>使用 {@code oauth2Login} 启用 OIDC 授权码流程，认证成功后由 Spring Security 建立本地 Session；</li>
 *   <li>不要设置 {@code SessionCreationPolicy.STATELESS}。这是服务端浏览器应用，
 *       本地 Session 是后续请求识别用户的载体。</li>
 * </ol>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            GrantedAuthoritiesMapper userAuthoritiesMapper,
            LogoutSuccessHandler oidcLogoutSuccessHandler) throws Exception {

        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/error", "/favicon.ico", "/public/**").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
                        // 把 Keycloak 的 Realm Role 转成 ROLE_ 前缀的 Spring Authority
                        .userInfoEndpoint(userInfo -> userInfo
                                .userAuthoritiesMapper(userAuthoritiesMapper))
                )
                // 登出时先清理本地 Session，再重定向到 Keycloak 的 end_session_endpoint
                .logout(logout -> logout
                        .logoutSuccessHandler(oidcLogoutSuccessHandler)
                );

        // CSRF 与 Session 均保持默认开启：浏览器 Web 应用必须防 CSRF
        return http.build();
    }
}
