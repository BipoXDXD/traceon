package bipo.tech.traceon.shared.web;

import static bipo.tech.traceon.testing.HttpClientForTests.get;
import static org.assertj.core.api.Assertions.assertThat;

import bipo.tech.traceon.testing.UnreachableDatabase;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Respostas fora do caminho feliz: rota desconhecida, encaminhamento de erro e headers de segurança. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HttpPipelineIT {

    private static final UnreachableDatabase DATABASE = UnreachableDatabase.refusingConnections();

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        DATABASE.register(registry);
    }

    @Test
    void unknownRouteReturnsProblemDetailsWithoutInternalDetails() {
        var response = get(port, "/this-route-does-not-exist");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.headers().firstValue("Content-Type")).hasValue("application/problem+json");
        assertThat(response.body())
                .contains("\"status\":404")
                .doesNotContainIgnoringCase("Exception")
                .doesNotContain("bipo.")
                .doesNotContain(" at ");
    }

    // /error existe só como destino do encaminhamento do Tomcat; chamado direto, não pode responder 500 nem 200.
    @Test
    void errorRouteCalledDirectlyLooksLikeAnUnknownRoute() {
        var response = get(port, "/error");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.headers().firstValue("Content-Type")).hasValue("application/problem+json");
    }

    // Exposta só no profile api-docs (OpenApiDocumentIT), nunca na configuração padrão, que é a de produção.
    @Test
    void openApiDocumentIsNotExposedWithoutTheApiDocsProfile() {
        var response = get(port, "/openapi/v1.json");

        assertThat(response.statusCode()).isEqualTo(404);
    }

    @ParameterizedTest
    @CsvSource({"/health/live, 200", "/health/ready, 503", "/this-route-does-not-exist, 404"})
    void responsesCarrySecurityHeaders(String path, int expectedStatus) {
        var response = get(port, path);

        assertThat(response.statusCode()).isEqualTo(expectedStatus);
        assertSecurityHeaders(response);
    }

    private static void assertSecurityHeaders(HttpResponse<String> response) {
        assertThat(response.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");
        assertThat(response.headers().firstValue("Referrer-Policy")).hasValue("no-referrer");
        assertThat(response.headers().firstValue("X-Frame-Options")).hasValue("DENY");
        assertThat(response.headers().firstValue("Content-Security-Policy"))
                .hasValue("default-src 'none'; frame-ancestors 'none'");
    }
}
