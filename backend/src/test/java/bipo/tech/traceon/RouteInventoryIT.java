package bipo.tech.traceon;

import static org.assertj.core.api.Assertions.assertThat;

import bipo.tech.traceon.testing.RegisteredRoutes;
import bipo.tech.traceon.testing.UnreachableDatabase;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Fitness function de "negar por padrão": toda rota registrada está na allowlist pública, então um endpoint novo não
 * entra sem decisão. Na etapa 2, com autenticação, este teste passa a exigir 401 sem credencial fora da allowlist.
 */
@SpringBootTest
class RouteInventoryIT {

    /**
     * Health e o destino interno do encaminhamento de erro do Tomcat, que chamado direto responde 404 (ver
     * {@code HttpPipelineIT}).
     */
    static final List<String> PUBLIC_ROUTES = List.of("* /error", "GET /health/live", "GET /health/ready");

    private static final UnreachableDatabase DATABASE = UnreachableDatabase.refusingConnections();

    @Autowired
    private ApplicationContext context;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        DATABASE.register(registry);
    }

    @Test
    void registeredRoutesAreExactlyThePublicAllowlist() {
        assertThat(RegisteredRoutes.of(context)).containsExactlyElementsOf(PUBLIC_ROUTES);
    }
}
