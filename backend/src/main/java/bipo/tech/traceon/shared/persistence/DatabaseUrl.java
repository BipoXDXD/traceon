package bipo.tech.traceon.shared.persistence;

import org.postgresql.Driver;

/** Regra de partida para {@code spring.datasource.url}; as mensagens nomeiam a chave, nunca o valor. */
final class DatabaseUrl {

    static final String KEY = "spring.datasource.url";

    private DatabaseUrl() {}

    static void requireValid(String url) {
        if (url == null || url.isBlank()) {
            throw new InvalidDatabaseConfigurationException(KEY
                    + " is required. Set the SPRING_DATASOURCE_URL environment variable"
                    + " (jdbc:postgresql://host:port/database).");
        }
        if (!isParsedByTheDriver(url)) {
            throw new InvalidDatabaseConfigurationException(KEY + " is not a valid PostgreSQL JDBC URL.");
        }
    }

    /**
     * O parser do driver devolve null para o que ele não aceitaria ao conectar (prefixo, porta, sintaxe). A exceção de
     * uma codificação inválida é descartada sem virar causa, porque a mensagem dela pode repetir o trecho da URL.
     */
    private static boolean isParsedByTheDriver(String url) {
        try {
            return Driver.parseURL(url, null) != null;
        } catch (IllegalArgumentException _) {
            return false;
        }
    }
}
