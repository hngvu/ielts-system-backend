package io.gsp26se16.moni.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .servers(List.of(new Server().url("http://localhost:8080").description("Local Server")))

                .info(new Info()
                        .title("IELTS  System API")
                        .version("v1.0")
                        .description("API Documentation for Intelligent Learning System for IELTS Skills Development")
                        .license(new License().name("Apache 2.0").url("http://springdoc.org")))

                // Cấu hình Security cho toàn bộ API
                .addSecurityItem(new SecurityRequirement().addList("bearer_token"))
                .components(new Components()
                        .addSecuritySchemes("bearer_token",
                                new SecurityScheme()
                                        .name("Authorization")
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }
}
