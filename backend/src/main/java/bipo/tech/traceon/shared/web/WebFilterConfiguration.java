package bipo.tech.traceon.shared.web;

import io.micrometer.tracing.Tracer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Os filtros rodam logo depois do {@code ServerHttpObservationFilter} do Boot ({@code HIGHEST_PRECEDENCE + 1}), que
 * abre o trace da requisição: o log da exceção precisa do trace aberto.
 */
@Configuration(proxyBeanMethods = false)
class WebFilterConfiguration {

    private static final int FIRST_AFTER_OBSERVATION = Ordered.HIGHEST_PRECEDENCE + 2;

    @Bean
    FilterRegistrationBean<SecurityHeadersFilter> securityHeadersFilter() {
        var registration = new FilterRegistrationBean<>(new SecurityHeadersFilter());
        registration.setOrder(FIRST_AFTER_OBSERVATION);
        return registration;
    }

    @Bean
    FilterRegistrationBean<UnhandledExceptionLoggingFilter> unhandledExceptionLoggingFilter(Tracer tracer) {
        var registration = new FilterRegistrationBean<>(new UnhandledExceptionLoggingFilter(tracer));
        registration.setOrder(FIRST_AFTER_OBSERVATION + 1);
        return registration;
    }
}
