package bipo.tech.traceon.health;

import static bipo.tech.traceon.testing.HttpClientForTests.get;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import bipo.tech.traceon.testing.UnreachableDatabase;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;

/**
 * Uma sonda que lança uma exceção inesperada exercita o tratamento real do 500, sem rota de teste na API. Só a
 * readiness depende de algo que pode lançar: a liveness não executa nada.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
class UnhandledExceptionIT {

    private static final String CANARY = "CANARY-41d7a9e2";

    private static final UnreachableDatabase DATABASE = UnreachableDatabase.refusingConnections();

    private static final List<String> FORBIDDEN_FRAGMENTS =
            List.of("Exception", "IllegalState", " at ", "bipo.", "traceon.", "jdbc:", "Host=", CANARY);

    @LocalServerPort
    private int port;

    @MockitoBean
    private DatabaseProbe databaseProbe;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        DATABASE.register(registry);
    }

    @BeforeEach
    void probeFailsUnexpectedly() {
        when(databaseProbe.check())
                .thenThrow(new IllegalStateException(CANARY + " Host=db jdbc:postgresql://db/traceon"));
    }

    @Test
    void unhandledExceptionReturnsGeneric500ProblemDetailsWithTraceId() {
        var response = get(port, "/health/ready");

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.headers().firstValue("Content-Type")).hasValue("application/problem+json");
        assertThat(traceIdOf(response.body())).matches("[0-9a-f]{32}");
        assertThat(response.body()).doesNotContain(FORBIDDEN_FRAGMENTS);
    }

    @Test
    void unhandledExceptionIsLoggedWithTheTraceIdReturnedToTheClient(CapturedOutput output) {
        var response = get(port, "/health/ready");

        String traceId = traceIdOf(response.body());
        assertThat(output.getAll().lines())
                .anySatisfy(line -> assertThat(line).contains(CANARY).contains(traceId));
    }

    @Test
    void unhandledExceptionResponseKeepsSecurityHeaders() {
        var response = get(port, "/health/ready");

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");
        assertThat(response.headers().firstValue("Referrer-Policy")).hasValue("no-referrer");
        assertThat(response.headers().firstValue("X-Frame-Options")).hasValue("DENY");
        assertThat(response.headers().firstValue("Content-Security-Policy"))
                .hasValue("default-src 'none'; frame-ancestors 'none'");
    }

    private static String traceIdOf(String problemDetails) {
        return new JsonMapper().readTree(problemDetails).path("traceId").asString("");
    }
}
