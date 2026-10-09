package bipo.tech.traceon.testing;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Um PostgreSQL para toda a execução dos testes, com a mesma imagem do compose.yaml. */
public final class PostgresTestDatabase {

    private static final String IMAGE = "postgres:18.6-alpine3.24";

    private static final PostgreSQLContainer CONTAINER = startContainer();

    private PostgresTestDatabase() {}

    public static void register(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", CONTAINER::getUsername);
        registry.add("spring.datasource.password", CONTAINER::getPassword);
    }

    public static String jdbcUrl() {
        return CONTAINER.getJdbcUrl();
    }

    public static String username() {
        return CONTAINER.getUsername();
    }

    public static String password() {
        return CONTAINER.getPassword();
    }

    // Parado pelo Ryuk do Testcontainers quando a JVM dos testes termina.
    private static PostgreSQLContainer startContainer() {
        var container = new PostgreSQLContainer(IMAGE);
        container.start();
        return container;
    }
}
