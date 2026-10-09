package bipo.tech.traceon.health;

import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.StringSchema;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;

/**
 * Documenta o {@code Cache-Control: no-store} das respostas de health. Só nos corpos de health (JSON): o 500 é escrito
 * pelo tratamento de erro, fora deste contrato.
 */
@Configuration(proxyBeanMethods = false)
class HealthApiDocumentation {

    private static final String HEALTH_PATH_PREFIX = "/health/";

    @Bean
    OpenApiCustomizer healthCacheControlHeader() {
        return openApi -> openApi.getPaths().entrySet().stream()
                .filter(path -> path.getKey().startsWith(HEALTH_PATH_PREFIX))
                .flatMap(path -> path.getValue().readOperations().stream())
                .flatMap(operation -> operation.getResponses().values().stream())
                .filter(response -> response.getContent() != null
                        && response.getContent().containsKey(MediaType.APPLICATION_JSON_VALUE))
                .forEach(response -> response.addHeaderObject("Cache-Control", noStoreHeader()));
    }

    private static Header noStoreHeader() {
        var schema = new StringSchema();
        schema.setEnum(List.of("no-store"));
        return new Header()
                .description("Sempre no-store: o estado de saúde muda a cada chamada e não pode ser reaproveitado.")
                .schema(schema);
    }
}
