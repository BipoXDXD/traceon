package bipo.tech.traceon.health;

import bipo.tech.traceon.testing.UnreachableDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Nada escuta na porta do banco: a conexão é recusada na hora. */
class RefusingDatabaseHealthIT extends UnreachableDatabaseHealthContract {

    private static final UnreachableDatabase DATABASE = UnreachableDatabase.refusingConnections();

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        DATABASE.register(registry);
    }

    @Override
    UnreachableDatabase database() {
        return DATABASE;
    }
}
