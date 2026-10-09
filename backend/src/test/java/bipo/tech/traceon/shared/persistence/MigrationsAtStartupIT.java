package bipo.tech.traceon.shared.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import bipo.tech.traceon.testing.PostgresTestDatabase;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Migrations nunca rodam na partida (ADR 0008): só por comando explícito. */
@SpringBootTest
class MigrationsAtStartupIT {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry);
    }

    @Test
    void startupDoesNotRunFlyway() throws SQLException {
        try (var connection = DriverManager.getConnection(
                        PostgresTestDatabase.jdbcUrl(),
                        PostgresTestDatabase.username(),
                        PostgresTestDatabase.password());
                var statement = connection.prepareStatement("select to_regclass('public.flyway_schema_history')");
                var result = statement.executeQuery()) {
            result.next();
            assertThat(result.getString(1)).isNull();
        }
    }
}
