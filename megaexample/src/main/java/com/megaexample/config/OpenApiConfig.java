package com.megaexample.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI megaOpenApi() {
        return new OpenAPI().info(new Info()
                .title("megaexample — TenantWall Load Test")
                .version("1.0.0")
                .description("30 tables, 2M rows. Every request requires X-Tenant-Id header."));
    }

    @Bean
    public OpenApiCustomizer tenantHeaderCustomizer() {
        return openApi -> openApi.getPaths().values().forEach(pathItem ->
                pathItem.readOperations().forEach(op ->
                        op.addParametersItem(new HeaderParameter()
                                .name("X-Tenant-Id")
                                .description("Tenant identifier (e.g., TENANT_001)")
                                .required(true))));
    }
}
