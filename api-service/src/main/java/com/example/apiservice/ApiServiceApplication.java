package com.example.apiservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Resource Server 启动类。
 *
 * <p>只负责验证调用方带来的 Access Token，不处理浏览器登录，
 * 也不应自己实现一套用户名密码校验逻辑。</p>
 */
@SpringBootApplication
public class ApiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiServiceApplication.class, args);
    }
}
