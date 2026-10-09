package bipo.tech.traceon;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Fronteira entre módulos de negócio (ADR 0001): sem ciclos e sem acesso aos pacotes internos de outro módulo. Só a
 * Por padrão, só o pacote raiz de cada módulo é visível aos demais; os subpacotes são internos.
 */
class ModularityTest {

    @Test
    void modulesRespectTheirBoundaries() {
        ApplicationModules.of(TraceonApplication.class).verify();
    }
}
