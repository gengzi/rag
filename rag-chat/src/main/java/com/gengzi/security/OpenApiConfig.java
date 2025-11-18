package com.gengzi.security;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    /**
     * 自定义API文档信息
     */
    @Bean
    public OpenAPI customOpenAPI() {
        // 定义 JWT 安全方案
        SecurityScheme securityScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .in(SecurityScheme.In.HEADER)
                .name("Authorization");

        // 全局添加安全要求（所有接口默认需要 JWT 认证）
        SecurityRequirement securityRequirement = new SecurityRequirement().addList("bearerAuth");
        return new OpenAPI()
                // API基本信息
                .info(new Info()
                        .title("Rag API 文档") // 文档标题
                        .description("Rag API 文档") // 文档描述
                        .version("v1.0.0") // 接口版本
                        // 联系人信息
                        .contact(new Contact()
                                .name("gengzi")
                                .email("gengzi@example.com"))
                        // 许可证信息
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0.html")))
                .components(new Components().addSecuritySchemes("bearerAuth", securityScheme))
                .addSecurityItem(securityRequirement);
    }


}
    