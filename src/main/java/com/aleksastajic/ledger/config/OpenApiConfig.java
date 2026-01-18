package com.aleksastajic.ledger.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@OpenAPIDefinition(
        info = @Info(
                title = "Banking Ledger API",
                description = "Double-entry ledger primitives with idempotent journal entry creation, reversals, and an integrity hash chain.",
                version = "v1"
        )
)
@Configuration
public class OpenApiConfig {
}
