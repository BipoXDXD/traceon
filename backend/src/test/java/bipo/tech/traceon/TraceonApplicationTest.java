package bipo.tech.traceon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * O {@code main} real, com a configuração que a fiação de partida precisa barrar. Os argumentos de linha de comando
 * vencem as variáveis de ambiente, então o resultado não depende do shell.
 */
@ExtendWith(OutputCaptureExtension.class)
class TraceonApplicationTest {

    private static final String CANARY = "CANARY-2b9d-entry-point";

    @Test
    void entryPointFailsToStartWithEmptyDatabaseUrl() {
        assertThatThrownBy(() -> TraceonApplication.main(arguments("--spring.datasource.url=")))
                .rootCause()
                .hasMessageContaining("spring.datasource.url");
    }

    @Test
    void entryPointFailureNeverLogsTheMalformedUrl(CapturedOutput output) {
        assertThatThrownBy(
                        () -> TraceonApplication.main(arguments("--spring.datasource.url=Host=db;Password=" + CANARY)))
                .rootCause()
                .hasMessageContaining("spring.datasource.url");
        assertThat(output.getAll()).contains("spring.datasource.url").doesNotContain(CANARY);
    }

    private static String[] arguments(String databaseUrl) {
        return new String[] {databaseUrl, "--server.port=0", "--spring.profiles.active=plain-logs"};
    }
}
