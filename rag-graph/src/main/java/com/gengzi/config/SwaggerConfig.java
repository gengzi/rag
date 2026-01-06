package com.gengzi.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger/OpenAPI 配置。
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI ragGraphOpenApi() {
        return new OpenAPI()
            .info(new Info()
                .title("RAG Graph API")
                .description("GraphRAG 构建与查询接口")
                .version("1.0.0"));
    }
}
