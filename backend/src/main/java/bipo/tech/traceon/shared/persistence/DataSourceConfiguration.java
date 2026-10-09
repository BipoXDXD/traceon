package bipo.tech.traceon.shared.persistence;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * O pool do Boot, com uma diferença: a partida falha se {@code spring.datasource.url} faltar ou não for uma URL JDBC
 * do PostgreSQL, e a mensagem nomeia a chave, nunca o valor, que pode trazer a senha. A validação feita pelo binder do
 * Boot repetiria o valor no log da falha.
 *
 * <p>O pool só abre conexão no primeiro uso: a aplicação sobe com o banco fora do ar, e a liveness continua respondendo
 * (ADR 0004).
 */
@Configuration(proxyBeanMethods = false)
class DataSourceConfiguration {

    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    HikariDataSource dataSource(DataSourceProperties properties) {
        DatabaseUrl.requireValid(properties.getUrl());
        return properties
                .initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }
}
