package com.example.ssoclient.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;

/**
 * OIDC RP-Initiated Logout 配置。
 *
 * <p>登出分两步：先销毁当前应用本地 Session，再重定向到 Keycloak 的
 * {@code end_session_endpoint} 结束中心会话（单点登出）。</p>
 *
 * <p>{@code setPostLogoutRedirectUri} 指向的地址必须登记在 Keycloak 客户端的
 * Valid post logout redirect URIs 中，否则 Keycloak 会拒绝跳回。</p>
 */
@Configuration
public class OidcLogoutConfig {

    @Bean
    public LogoutSuccessHandler oidcLogoutSuccessHandler(
            ClientRegistrationRepository clientRegistrationRepository) {
        OidcClientInitiatedLogoutSuccessHandler handler =
                new OidcClientInitiatedLogoutSuccessHandler(clientRegistrationRepository);
        handler.setPostLogoutRedirectUri("{baseUrl}/");
        return handler;
    }
}
