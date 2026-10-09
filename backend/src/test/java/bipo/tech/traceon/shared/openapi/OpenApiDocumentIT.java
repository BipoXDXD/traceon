package bipo.tech.traceon.shared.openapi;

import static bipo.tech.traceon.testing.HttpClientForTests.get;
import static org.assertj.core.api.Assertions.assertThat;

import bipo.tech.traceon.testing.RegisteredRoutes;
import bipo.tech.traceon.testing.UnreachableDatabase;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A spec que a aplicação gera no profile api-docs: o que ela declara e a cópia versionada em
 * {@code docs/api/openapi.json}, que a CI lê com o Spectral (ADR 0007).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("api-docs")
class OpenApiDocumentIT {

    static final String SPEC_PATH = "/openapi/v1.json";

    private static final Path COMMITTED_SPEC = Path.of("..", "docs", "api", "openapi.json");
    private static final Path GENERATED_SPEC = Path.of("target", "openapi.json");

    private static final UnreachableDatabase DATABASE = UnreachableDatabase.refusingConnections();

    private static final JsonMapper JSON = new JsonMapper();

    @LocalServerPort
    private int port;

    @Autowired
    private ApplicationContext context;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        DATABASE.register(registry);
    }

    @Test
    void committedSpecMatchesTheGeneratedOne() throws IOException {
        String generated = generatedSpec();
        Files.writeString(GENERATED_SPEC, generated, StandardCharsets.UTF_8);

        assertThat(COMMITTED_SPEC)
                .as("spec ausente; gere com ./mvnw verify e copie backend/%s para docs/api", GENERATED_SPEC)
                .exists();
        assertThat(JSON.readTree(COMMITTED_SPEC.toFile()))
                .as(
                        "docs/api/openapi.json desatualizada; depois de ./mvnw verify, copie backend/%s para ela e revise"
                                + " o diff",
                        GENERATED_SPEC)
                .isEqualTo(JSON.readTree(generated));
    }

    @Test
    void documentListsExactlyTheTwoHealthGetOperations() {
        JsonNode paths = JSON.readTree(generatedSpec()).path("paths");

        assertThat(paths.propertyNames()).containsExactly("/health/live", "/health/ready");
        assertThat(paths.path("/health/live").propertyNames()).containsExactly("get");
        assertThat(paths.path("/health/ready").propertyNames()).containsExactly("get");
    }

    @ParameterizedTest
    @CsvSource({"/health/live, getLiveness, 200", "/health/ready, getReadiness, 200 503"})
    void healthOperationDocumentsItsIdentityAndJsonResponses(String path, String operationId, String statuses) {
        JsonNode operation =
                JSON.readTree(generatedSpec()).path("paths").path(path).path("get");
        List<String> healthStatuses = List.of(statuses.split(" "));

        assertThat(operation.path("operationId").asString()).isEqualTo(operationId);
        assertThat(operation.path("summary").asString()).isNotBlank();
        assertThat(operation.path("description").asString()).contains("diagnóstico");
        assertThat(operation.path("tags").valueStream().map(JsonNode::asString)).containsExactly("Health");

        JsonNode responses = operation.path("responses");
        assertThat(responses.propertyNames()).containsExactlyInAnyOrderElementsOf(concat(healthStatuses, "500"));
        assertThat(healthStatuses).allSatisfy(status -> {
            JsonNode response = responses.path(status);
            assertThat(response.at("/content/application~1json/schema").isMissingNode())
                    .as("%s %s documenta o schema JSON", path, status)
                    .isFalse();
            assertThat(response.at("/headers/Cache-Control").isMissingNode())
                    .as("%s %s documenta o Cache-Control", path, status)
                    .isFalse();
        });
        assertThat(responses.at("/500/content/application~1problem+json").isMissingNode())
                .as("%s 500 documenta o Problem Details", path)
                .isFalse();
    }

    // O springdoc publica a mesma spec também em YAML; as duas só existem neste profile.
    @Test
    void apiDocsProfileAddsOnlyTheSpecRoutesToTheAllowlist() {
        assertThat(RegisteredRoutes.of(context))
                .containsExactly(
                        "* /error",
                        "GET /health/live",
                        "GET /health/ready",
                        "GET " + SPEC_PATH,
                        "GET " + SPEC_PATH + ".yaml");
    }

    private String generatedSpec() {
        var response = get(port, SPEC_PATH);
        assertThat(response.statusCode()).isEqualTo(200);
        return response.body();
    }

    private static List<String> concat(List<String> list, String last) {
        return Stream.concat(list.stream(), Stream.of(last)).toList();
    }
}
