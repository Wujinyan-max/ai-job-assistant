package com.jobassistant.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 接口文档配置，访问地址：http://localhost:8088/api/doc.html
 */
@Configuration
public class Knife4jConfig {

    private static final String TOKEN_SCHEME = "JWT";

    @Bean
    public OpenAPI jobAssistantOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("AI 求职管理平台 API")
                        .description("职位投递 / 简历管理 / 面试跟踪 / AI 分析")
                        .version("1.0.0")
                        .contact(new Contact().name("AI Job Assistant")))
                .components(new Components().addSecuritySchemes(TOKEN_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .in(SecurityScheme.In.HEADER)
                                .name("Authorization")))
                .addSecurityItem(new SecurityRequirement().addList(TOKEN_SCHEME));
    }
}
