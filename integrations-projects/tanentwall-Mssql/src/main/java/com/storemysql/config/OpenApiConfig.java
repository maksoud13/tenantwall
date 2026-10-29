package com.storeMSsql.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI storeOpenApi() {
        return new OpenAPI().info(new Info()
                .title("TenantWall Store Example")
                .version("1.0.0")
                .description("Multi-tenant store demo — add header X-Tenant-Id to every request"));
    }

    @Bean
    public OpenApiCustomizer tenantHeaderCustomizer() {
        return openApi -> openApi.getPaths().values().forEach(pathItem ->
                pathItem.readOperations().forEach(op ->
                        op.addParametersItem(new HeaderParameter()
                                .name("X-Tenant-Id")
                                .description("Tenant identifier (e.g., SHOP_ALPHA)")
                                .required(true))));
    }
}