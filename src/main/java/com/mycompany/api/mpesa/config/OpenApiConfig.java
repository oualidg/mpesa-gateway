/*
 * Copyright (c) 2026 Oualid Gharach. All rights reserved.
 *
 * Aiming for production-grade standards through clean code and best practices
 * for educational and informational purposes.
 *
 * Created on: 5/13/2026 at 2:05 PM
 *
 * Feel free to use or contribute. Contact: oualid.gharach@gmail.com
 */
package com.mycompany.api.mpesa.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.QueryParameter;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI/Swagger configuration for the M-Pesa Gateway.
 *
 * <p>Exposes a single API group covering the two Safaricom C2B callback endpoints:</p>
 * <ul>
 *   <li>POST /mpesa/v1/validation   -- pre-payment check, synchronous</li>
 *   <li>POST /mpesa/v1/confirmation -- post-payment notification, asynchronous</li>
 * </ul>
 *
 * <p>No admin or management endpoints are exposed via Swagger -- the Gateway
 * has no admin surface. Actuator endpoints (health, info, prometheus) are
 * accessible directly but excluded from the API documentation.</p>
 *
 * <p>No authentication scheme is registered. Callback token validation is
 * enforced upstream by {@code CallbackTokenFilter} before any controller is
 * reached -- it is a filter-level concern, not a per-endpoint scheme.</p>
 *
 * <p>The {@code token} query parameter is pre-filled in Swagger UI from
 * {@code app.callback.token} so testing does not require manual token entry.
 * The default value matches the dev token in application.properties.</p>
 *
 * <p>Swagger UI is only accessible directly via the server's internal IP on
 * port 8081 from the local network -- it is not routed through Nginx.</p>
 *
 * Access Swagger UI at: http://192.168.1.168:8081/swagger-ui.html
 *
 * @author Oualid Gharach
 */
@Configuration
public class OpenApiConfig {

    @Value("${info.app.name}")
    private String appName;

    @Value("${info.app.description}")
    private String appDescription;

    @Value("${info.app.version}")
    private String appVersion;

    @Value("${info.app.author}")
    private String author;

    @Value("${info.app.contact.email}")
    private String email;

    /**
     * Callback token read from configuration.
     * Defaults to the dev token so Swagger works out of the box without
     * any environment variable set. In production the .env value overrides this.
     */
    @Value("${app.callback.token}")
    private String callbackToken;


    /**
     * Global OpenAPI metadata -- title, version, description, contact.
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title(appName)
                        .version(appVersion)
                        .description(appDescription)
                        .contact(new Contact()
                                .name(author)
                                .email(email)));
    }

    /**
     * M-Pesa Callbacks group -- always visible in all profiles.
     *
     * <p>Covers the two Safaricom C2B callback endpoints. Useful for local
     * testing via Swagger UI or Postman against the internal IP, and for
     * k6 load testing of the confirmation ingest path.</p>
     *
     * <p>No security scheme is applied -- callback token authentication is
     * a filter-level concern handled by {@code CallbackTokenFilter}.</p>
     */
    @Bean
    public GroupedOpenApi mpesaCallbacksGroup() {
        return GroupedOpenApi.builder()
                .group("mpesa-callbacks")
                .displayName("M-Pesa Callbacks")
                .pathsToMatch("/mpesa/v1/**")
                .addOpenApiCustomizer(callbackTokenParameterCustomizer())
                .build();
    }

    /**
     * Adds the {@code token} query parameter to all M-Pesa callback endpoints
     * in Swagger UI, pre-filled with the configured callback token.
     *
     * <p>This allows Swagger testing without manually entering the token on
     * every request. The value is read from {@code app.callback.token} so it
     * stays in sync with the running configuration automatically.</p>
     */
    public OpenApiCustomizer callbackTokenParameterCustomizer() {
        return openApi -> openApi.getPaths().forEach((path, item) -> {
            if (path.startsWith("/mpesa/")) {
                QueryParameter tokenParam = (QueryParameter) new QueryParameter()
                        .name("token")
                        .required(true)
                        .description("Callback secret token.")
                        .schema(new StringSchema()._default(callbackToken));

                if (item.getPost() != null) {
                    item.getPost().addParametersItem(tokenParam);
                }
            }
        });
    }
}