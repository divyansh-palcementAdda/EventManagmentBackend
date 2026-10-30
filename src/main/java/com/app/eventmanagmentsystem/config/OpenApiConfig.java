package com.app.eventmanagmentsystem.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 3 / Swagger configuration.
 *
 * Configures JWT Bearer authentication scheme so that authorized endpoints can be tested
 * directly through Swagger UI by pasting the JWT access token once.
 */
@Configuration
public class OpenApiConfig {

    public static final String SECURITY_SCHEME_NAME = "Bearer Authentication";

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("EventPass Management System API")
                        .version("1.0.0")
                        .description("""
                                ### Secure Event Pass Management Backend
                                
                                This REST API powers the Event Pass Management System:
                                - **Authentication**: JWT-based login (`/api/v1/auth/login`).
                                - **Events**: Event creation, status updates, and listing (`/api/v1/events`).
                                - **Gatekeeper Management**: Event-specific gatekeeper assignments (`/api/v1/events/{eventId}/gatekeepers`).
                                - **Passes**: Batch sequential pass generation, activation, and inspection (`/api/v1/passes`).
                                - **Check-In**: Single-use transactional QR entry verification (`/api/v1/checkin`).
                                
                                #### Testing with Swagger UI:
                                1. Call `POST /api/v1/auth/login` with your credentials to obtain a JWT.
                                2. Click the **Authorize** button (top right or below) and paste the JWT token into the **Value** box.
                                3. All protected endpoints will now include the `Authorization: Bearer <token>` header automatically.
                                """)
                        .contact(new Contact()
                                .name("EventPass Engineering Team")
                                .email("dev@eventpass.local"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .servers(List.of(
                        new Server().url("/").description("Current Server")
                ))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Enter your Bearer JWT token (e.g. eyJhbGciOi...)")));
    }
}

