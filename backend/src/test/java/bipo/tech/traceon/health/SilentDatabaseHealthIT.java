package bipo.tech.traceon.health;

import bipo.tech.traceon.testing.UnreachableDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * O banco aceita o TCP e nunca responde: só o timeout da sonda encerra a espera. O socket fica aberto até a JVM dos
 * testes terminar, porque o contexto do Spring fica em cache depois desta classe.
 */
class SilentDatabaseHealthIT extends UnreachableDatabaseHealthContract {

    private static final UnreachableDatabase DATABASE = UnreachableDatabase.neverResponding();

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        DATABASE.register(registry);
    }

    @Override
    UnreachableDatabase database() {
        return DATABASE;
    }
}
