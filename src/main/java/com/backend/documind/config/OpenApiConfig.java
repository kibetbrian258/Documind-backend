package com.backend.documind.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadata shown at the top of Swagger UI (/swagger-ui.html).
 * Endpoint documentation is generated automatically from the controllers.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI documindOpenApi() {
        return new OpenAPI().info(new Info()
                .title("DocuMind API")
                .version("v1")
                .description("Upload your documents and ask questions about them. "
                        + "Every answer cites the document and page it came from."));
    }
}