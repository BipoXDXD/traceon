package bipo.tech.traceon.health;

import static bipo.tech.traceon.testing.HttpClientForTests.get;
import static org.assertj.core.api.Assertions.assertThat;

import bipo.tech.traceon.testing.UnreachableDatabase;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Health com o banco inalcançável, nas duas formas que o harness simula. Cada subclasse fornece o banco e a
 * {@code @DynamicPropertySource}; os testes valem para as duas.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
abstract class UnreachableDatabaseHealthContract {

    private static final String UNHEALTHY_READY_BODY = """
            {"status":"Unhealthy","checks":[{"name":"database","status":"Unhealthy"}]}""";

    // A sonda dá 3 s ao banco; a margem absorve máquinas lentas da CI.
    private static final Duration MAX_UNHEALTHY_READINESS_DURATION = Duration.ofSeconds(10);

    @LocalServerPort
    private int port;

    abstract UnreachableDatabase database();

    @Test
    void liveReturnsHealthyWhenDatabaseIsUnreachable() {
        var response = get(port, "/health/live");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(HealthEndpointIT.HEALTHY_LIVE_BODY);
    }

    @Test
    void ready503UnhealthyPromptlyWhenDatabaseIsUnreachable() {
        long start = System.nanoTime();
        var response = get(port, "/health/ready");
        var elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.body()).isEqualTo(UNHEALTHY_READY_BODY);
        assertThat(elapsed).isLessThan(MAX_UNHEALTHY_READINESS_DURATION);
        // O corpo exato já exclui estes; a lista deixa explícita a intenção de segurança.
        assertThat(response.body())
                .doesNotContain(UnreachableDatabase.CANARY_PASSWORD)
                .doesNotContain(Integer.toString(database().port()))
                .doesNotContainIgnoringCase("jdbc:")
                .doesNotContainIgnoringCase("Exception")
                .doesNotContainIgnoringCase("postgresql")
                .doesNotContain(" at ");
    }

    @Test
    void readyFailureLogsNeverContainTheDatabasePassword(CapturedOutput output) {
        var response = get(port, "/health/ready");

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(output.getAll())
                .contains("Health check database")
                .doesNotContain(UnreachableDatabase.CANARY_PASSWORD);
    }
}
