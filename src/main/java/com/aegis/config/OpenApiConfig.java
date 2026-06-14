package com.aegis.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 문서 설정. JWT Bearer 보안 스킴을 등록해 Swagger UI 의 Authorize 버튼으로
 * 토큰을 넣고 보호 자원을 호출할 수 있게 한다.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI aegisOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Aegis API")
                        .version("0.1.0")
                        .description("방어형 웹 보안 시스템 — 인증/탐지/방어/감사/대시보드"))
                .components(new Components()
                        .addSecuritySchemes("bearer", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
