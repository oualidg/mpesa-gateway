/*
 * Copyright (c) 2026 Oualid Gharach. All rights reserved.
 *
 * Aiming for production-grade standards through clean code and best practices
 * for educational and informational purposes.
 *
 * Created on: 5/14/2026 at 10:30 PM
 *
 * Feel free to use or contribute. Contact: oualid.gharach@gmail.com
 */
package com.mycompany.api.mpesa.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.swagger.v3.oas.models.parameters.QueryParameter;
import org.springdoc.core.customizers.OpenApiCustomizer;

/**
 * OpenAPI/Swagger configuration for the M-Pesa Gateway.
 *
 * <p>Swagger UI is available at {@code http://localhost:8081/swagger-ui.html}.
 *
 * @author Oualid Gharach
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("M-Pesa Gateway")
                        .version("1.0.0")
                        .description("Safaricom C2B Gateway — validation and confirmation callback endpoints")
                        .contact(new Contact()
                                .name("Oualid Gharach")
                                .email("oualid.gharach@gmail.com")));
    }

    @Bean
    public OpenApiCustomizer callbackTokenParameterCustomizer() {
        return openApi -> openApi.getPaths().forEach((path, item) -> {
            if (path.startsWith("/mpesa/")) {
                io.swagger.v3.oas.models.parameters.Parameter tokenParam =
                        new QueryParameter()
                                .name("token")
                                .required(true)
                                .description("Callback secret token")
                                .schema(new io.swagger.v3.oas.models.media.StringSchema()
                                        ._default("dev-callback-token"));

                if (item.getPost() != null) {
                    item.getPost().addParametersItem(tokenParam);
                }
            }
        });
    }

}