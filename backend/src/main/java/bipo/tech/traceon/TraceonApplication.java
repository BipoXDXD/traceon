package bipo.tech.traceon;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Composition root: os módulos de negócio são os subpacotes diretos deste pacote (ADR 0001). */
@SpringBootApplication(proxyBeanMethods = false)
public final class TraceonApplication {

    public static void main(String[] args) {
        SpringApplication.run(TraceonApplication.class, args);
    }
}
