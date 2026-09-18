package com.schwab.auditlog.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Metadata shown at the top of the generated Swagger UI / OpenAPI spec. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI auditLogOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Tamper-Evident Audit Log Service")
                .description("Append-only, hash-chained audit log. Records cannot be updated or deleted "
                        + "through this API; GET /audit/verify walks the chain and reports any tampering.")
                .version("1.0.0"));
    }
}