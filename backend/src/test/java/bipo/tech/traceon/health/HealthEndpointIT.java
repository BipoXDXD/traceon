package bipo.tech.traceon.health;

import static bipo.tech.traceon.testing.HttpClientForTests.get;
import static bipo.tech.traceon.testing.HttpClientForTests.send;
import static org.assertj.core.api.Assertions.assertThat;

import bipo.tech.traceon.testing.PostgresTestDatabase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Health com o banco disponível: contrato exato lido pelo frontend. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HealthEndpointIT {

    static final String HEALTHY_LIVE_BODY = """
            {"status":"Healthy"}""";

    private static final String HEALTHY_READY_BODY = """
            {"status":"Healthy","checks":[{"name":"database","status":"Healthy"}]}""";

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry);
    }

    @Test
    void liveReturnsHealthyWhenDatabaseIsAvailable() {
        var response = get(port, "/health/live");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(HEALTHY_LIVE_BODY);
    }

    @Test
    void readyReturnsHealthyDatabaseCheckWhenDatabaseIsAvailable() {
        var response = get(port, "/health/ready");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(HEALTHY_READY_BODY);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/health/live", "/health/ready"})
    void healthResponsesAreUncacheableJson(String path) {
        var response = get(port, path);

        assertThat(response.headers().firstValue("Content-Type")).hasValue("application/json");
        assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-store");
    }

    @ParameterizedTest
    @CsvSource({
        "POST, /health/live",
        "PUT, /health/live",
        "DELETE, /health/live",
        "POST, /health/ready",
        "PUT, /health/ready",
        "DELETE, /health/ready"
    })
    void healthEndpointsRejectOtherMethodsWith405ProblemDetails(String method, String path) {
        var response = send(port, method, path);

        assertThat(response.statusCode()).isEqualTo(405);
        assertThat(response.headers().firstValue("Content-Type")).hasValue("application/problem+json");
        assertThat(response.body()).contains("\"status\":405");
        assertThat(response.headers().allValues("Allow")).containsExactly("GET");
    }
}
