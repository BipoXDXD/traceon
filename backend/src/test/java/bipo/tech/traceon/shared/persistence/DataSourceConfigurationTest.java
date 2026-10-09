package bipo.tech.traceon.shared.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;

/**
 * A partida falha sem {@code spring.datasource.url} válida, sem repetir o valor. As variáveis de ambiente da máquina
 * ficam de fora, para o caso "ausente" ser real e não depender do shell.
 */
class DataSourceConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(context -> context.getEnvironment()
                    .getPropertySources()
                    .remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME))
            .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class))
            .withUserConfiguration(DataSourceConfiguration.class);

    @Test
    void contextFailsToStartWithoutDatabaseUrl() {
        runner.run(context -> assertThat(context)
                .getFailure()
                .rootCause()
                .isInstanceOf(InvalidDatabaseConfigurationException.class)
                .hasMessageContaining("spring.datasource.url"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void contextFailsToStartWithBlankDatabaseUrl(String url) {
        runner.withPropertyValues("spring.datasource.url=" + url)
                .run(context -> assertThat(context)
                        .getFailure()
                        .rootCause()
                        .isInstanceOf(InvalidDatabaseConfigurationException.class)
                        .hasMessageContaining("spring.datasource.url"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                // Connection string no formato do Npgsql, o engano mais provável vindo do backend .NET
                "Host=localhost;Password=CANARY-2b9d-malformed;Database=traceon",
                "jdbc:mysql://localhost/CANARY-2b9d-malformed",
                "jdbc:postgresql://localhost:not-a-port/CANARY-2b9d-malformed",
                "jdbc:postgresql://localhost/traceon?password=CANARY-2b9d-malformed&x=%zz"
            })
    void contextFailsToStartWithMalformedDatabaseUrlWithoutEchoingIt(String url) {
        runner.withPropertyValues("spring.datasource.url=" + url).run(context -> {
            assertThat(context)
                    .getFailure()
                    .rootCause()
                    .isInstanceOf(InvalidDatabaseConfigurationException.class)
                    .hasMessageContaining("spring.datasource.url");
            assertThat(stackTraceOf(context.getStartupFailure())).doesNotContain("CANARY-2b9d-malformed");
        });
    }

    @Test
    void contextStartsWithAValidUrlWithoutConnecting() {
        // Porta 1 no loopback: ninguém escuta. Subir mesmo assim prova que a partida não abre conexão.
        runner.withPropertyValues("spring.datasource.url=jdbc:postgresql://127.0.0.1:1/traceon")
                .run(context -> assertThat(context).hasNotFailed());
    }

    private static String stackTraceOf(Throwable failure) {
        var writer = new StringWriter();
        failure.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }
}
