package bipo.tech.traceon.shared.web;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.filter.ServerHttpObservationFilter;

/**
 * Registra a exceção que escapou da aplicação ainda dentro do trace da requisição, para o stack trace sair no log com
 * o trace id. Guarda esse id na requisição e responde 500 pelo encaminhamento de erro, onde o
 * {@link ProblemDetailErrorController} monta o corpo sem a causa e com o mesmo id.
 */
final class UnhandledExceptionLoggingFilter extends OncePerRequestFilter {

    static final String TRACE_ID_ATTRIBUTE = UnhandledExceptionLoggingFilter.class.getName() + ".traceId";

    private static final Logger log = LoggerFactory.getLogger(UnhandledExceptionLoggingFilter.class);

    private final Tracer tracer;

    UnhandledExceptionLoggingFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException exception) {
            log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), exception);
            if (response.isCommitted()) {
                throw exception;
            }
            Span span = tracer.currentSpan();
            if (span != null) {
                request.setAttribute(TRACE_ID_ATTRIBUTE, span.context().traceId());
            }
            // A métrica http.server.requests continua registrando a exceção, como se ela tivesse chegado ao Tomcat.
            ServerHttpObservationFilter.findObservationContext(request)
                    .ifPresent(context -> context.setError(exception));
            response.sendError(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
    }
}
