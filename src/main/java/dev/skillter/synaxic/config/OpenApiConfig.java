package dev.skillter.synaxic.config;

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

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "ApiKeyAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("Synaxic API")
                        .version("1.0.0")
                        .description("An innovative API Hub for developers. Obtain your API key by signing in with Google.")
                        .contact(new Contact()
                                .name("Skillter Dev")
                                .email("api@skillter.dev")
                                .url("https://synaxic.skillter.dev"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local development server"),
                        new Server().url("https://api.synaxic.skillter.dev").description("Production server")
                ))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("Authorization")
                                .description("Enter your API key prefixed with 'ApiKey '. Example: `ApiKey syn_live_...`." +
                                        "<br>Alternatively, you can use the `X-API-Key` header.")));
    }
}