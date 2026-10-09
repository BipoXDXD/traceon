package bipo.tech.traceon.health;

import com.fasterxml.jackson.annotation.JsonValue;

/** Estados do contrato de health, na grafia que o frontend lê, do pior para o melhor: a ordem natural é a gravidade. */
enum HealthStatus {
    UNHEALTHY("Unhealthy"),
    DEGRADED("Degraded"),
    HEALTHY("Healthy");

    private final String contractName;

    HealthStatus(String contractName) {
        this.contractName = contractName;
    }

    @JsonValue
    String contractName() {
        return contractName;
    }

    /** {@code Degraded} ainda atende tráfego: só {@code Unhealthy} tira a instância de rotação. */
    boolean servesTraffic() {
        return this != UNHEALTHY;
    }
}
