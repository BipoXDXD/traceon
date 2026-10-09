package bipo.tech.traceon.health;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.stereotype.Component;

/**
 * Sonda de prontidão: abre uma conexão real e sem pool, limitada a {@link #TIMEOUT}.
 *
 * <p>Fora do pool de propósito: uma conexão ociosa do pool pode voltar sem ida à rede e dar um banco morto como
 * pronto. O timeout curto vale só para a sonda; as conexões da aplicação seguem a configuração do operador. A falha
 * vai para o log sem a URL nem a senha (as mensagens do driver não as trazem), e a resposta HTTP só leva o estado.
 */
@Component
class DatabaseProbe {

    static final Duration TIMEOUT = Duration.ofSeconds(3);

    private static final Logger log = LoggerFactory.getLogger(DatabaseProbe.class);

    private final DataSourceProperties dataSource;

    DatabaseProbe(DataSourceProperties dataSource) {
        this.dataSource = dataSource;
    }

    HealthStatus check() {
        try (Connection _ = DriverManager.getConnection(dataSource.getUrl(), connectionProperties())) {
            return HealthStatus.HEALTHY;
        } catch (SQLException exception) {
            log.warn("Health check database is Unhealthy: {}", exception.getMessage());
            return HealthStatus.UNHEALTHY;
        }
    }

    /**
     * loginTimeout cobre a conexão inteira, inclusive a espera pela resposta de um servidor que aceitou o TCP e ficou
     * mudo; connectTimeout e socketTimeout limitam cada etapa. Os três em segundos, como o driver espera.
     */
    private Properties connectionProperties() {
        var properties = new Properties();
        if (dataSource.getUsername() != null) {
            properties.setProperty("user", dataSource.getUsername());
        }
        if (dataSource.getPassword() != null) {
            properties.setProperty("password", dataSource.getPassword());
        }
        String timeoutSeconds = Long.toString(TIMEOUT.toSeconds());
        properties.setProperty("loginTimeout", timeoutSeconds);
        properties.setProperty("connectTimeout", timeoutSeconds);
        properties.setProperty("socketTimeout", timeoutSeconds);
        return properties;
    }
}
