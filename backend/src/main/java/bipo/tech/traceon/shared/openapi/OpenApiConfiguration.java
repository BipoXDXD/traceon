package bipo.tech.traceon.shared.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.servers.Server;
import java.math.BigDecimal;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;

/**
 * O que o springdoc não deduz sozinho: os metadados do documento e o 500 em Problem Details, que vale para toda
 * operação e não sai do controller. A spec versionada é {@code docs/api/openapi.json} (ADR 0007).
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {

    private static final String PROBLEM_SCHEMA = "ProblemDetail";
    private static final String PROBLEM_REF = "#/components/schemas/" + PROBLEM_SCHEMA;

    /** Teto dos textos do Problem Details: só título e detalhe curtos, nunca stack trace. */
    private static final int PROBLEM_TEXT_MAX_LENGTH = 1_000;

    private static final int MIN_HTTP_STATUS = 100;
    private static final int MAX_HTTP_STATUS = 599;

    /** O trace id W3C, em hexadecimal minúsculo. */
    private static final String TRACE_ID_PATTERN = "^[0-9a-f]{32}$";

    private static final int TRACE_ID_LENGTH = 32;

    @Bean
    OpenAPI traceonOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Traceon API").version("1.0.0").description("""
                                API do Traceon, SaaS de monitoramento de integridade de aplicações web. Na etapa \
                                Foundation expõe apenas os endpoints de saúde (liveness e readiness), sem \
                                autenticação e sem dados de negócio."""))
                // Relativo: o navegador chega à API pela origem do frontend, por proxy (ADR 0006), e um host
                // tornaria a spec versionada dependente de onde foi gerada.
                .servers(List.of(new Server().url("/").description("Mesma origem do frontend")));
    }

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    OpenApiCustomizer unexpectedErrorResponse() {
        return openApi -> {
            // Aqui, e não no bean OpenAPI: lá o springdoc descarta os schemas que nenhuma anotação referencia.
            openApi.getComponents().addSchemas(PROBLEM_SCHEMA, problemDetailSchema());
            openApi.getPaths().values().stream()
                    .flatMap(path -> path.readOperations().stream())
                    .forEach(operation -> operation.getResponses().addApiResponse("500", unexpectedError()));
        };
    }

    private static ApiResponse unexpectedError() {
        var mediaType = new io.swagger.v3.oas.models.media.MediaType().schema(new Schema<>().$ref(PROBLEM_REF));
        return new ApiResponse()
                .description("Internal Server Error")
                .content(new Content().addMediaType(MediaType.APPLICATION_PROBLEM_JSON_VALUE, mediaType));
    }

    /** O formato que a API de fato escreve (RFC 9457), em vez do que o springdoc deduz da classe do Spring. */
    private static Schema<?> problemDetailSchema() {
        var schema = new ObjectSchema();
        schema.setDescription("Erro no formato RFC 9457. Sem stack trace, SQL nem nome de classe.");
        schema.addProperty("type", new StringSchema().format("uri-reference").maxLength(PROBLEM_TEXT_MAX_LENGTH));
        schema.addProperty("title", new StringSchema().maxLength(PROBLEM_TEXT_MAX_LENGTH));
        schema.addProperty(
                "status",
                new IntegerSchema()
                        .format("int32")
                        .minimum(BigDecimal.valueOf(MIN_HTTP_STATUS))
                        .maximum(BigDecimal.valueOf(MAX_HTTP_STATUS)));
        schema.addProperty("detail", new StringSchema().maxLength(PROBLEM_TEXT_MAX_LENGTH));
        schema.addProperty(
                "instance", new StringSchema().format("uri-reference").maxLength(PROBLEM_TEXT_MAX_LENGTH));
        var traceId = new StringSchema();
        traceId.setPattern(TRACE_ID_PATTERN);
        traceId.setMinLength(TRACE_ID_LENGTH);
        traceId.setMaxLength(TRACE_ID_LENGTH);
        traceId.setDescription("Trace id W3C da requisição; vem no 500 e é o mesmo do log do erro");
        schema.addProperty("traceId", traceId);
        schema.setRequired(List.of("title", "status"));
        return schema;
    }
}
