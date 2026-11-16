package com.example.ssoclient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 浏览器端 OIDC 客户端启动类。
 *
 * <p>同一份构建产物既可作为 App-A 启动，也可作为 App-B 启动，
 * 差异只体现在环境变量上：端口、client-id、client-secret 和会话 Cookie 名。</p>
 */
@SpringBootApplication
public class SsoClientApplication {

    public static void main(String[] args) {
        SpringApplication.run(SsoClientApplication.class, args);
    }
}
