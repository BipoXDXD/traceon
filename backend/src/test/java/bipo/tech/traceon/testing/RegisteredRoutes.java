package bipo.tech.traceon.testing;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.handler.AbstractUrlHandlerMapping;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.RequestMappingInfoHandlerMapping;

/**
 * Toda rota que o MVC atende, como "MÉTODO padrão". Um mapeamento sem método atende qualquer verbo: "*" deixa isso à
 * vista em vez de escondê-lo. Inclui os mapeamentos por URL (arquivos estáticos, por exemplo), que não passam por
 * controller.
 */
public final class RegisteredRoutes {

    private RegisteredRoutes() {}

    public static List<String> of(ApplicationContext context) {
        Stream<String> controllerRoutes =
                context.getBeansOfType(RequestMappingInfoHandlerMapping.class).values().stream()
                        .flatMap(mapping -> mapping.getHandlerMethods().keySet().stream())
                        .flatMap(RegisteredRoutes::describe);
        Stream<String> urlRoutes = context.getBeansOfType(AbstractUrlHandlerMapping.class).values().stream()
                .flatMap(mapping -> mapping.getHandlerMap().keySet().stream())
                .map(pattern -> "* " + pattern);
        return Stream.concat(controllerRoutes, urlRoutes).sorted().toList();
    }

    private static Stream<String> describe(RequestMappingInfo info) {
        Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
        Stream<String> verbs =
                methods.isEmpty() ? Stream.of("*") : methods.stream().map(RequestMethod::name);
        return verbs.flatMap(verb -> info.getPatternValues().stream().map(pattern -> verb + " " + pattern));
    }
}
