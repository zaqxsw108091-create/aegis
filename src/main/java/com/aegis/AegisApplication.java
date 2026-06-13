package com.aegis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Aegis - 방어형 웹 보안 시스템
 * 무차별 대입 / SQLi / XSS / 요청 폭주로부터 애플리케이션을 보호한다.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class AegisApplication {
    public static void main(String[] args) {
        SpringApplication.run(AegisApplication.class, args);
    }
}
