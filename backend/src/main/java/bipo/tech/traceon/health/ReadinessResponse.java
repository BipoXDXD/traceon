package bipo.tech.traceon.health;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Comparator;
import java.util.List;

/** Corpo de {@code /health/ready}: o estado geral é o pior entre as verificações. */
record ReadinessResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) HealthStatus status,

        @ArraySchema(maxItems = MAX_CHECKS, arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED))
        List<CheckResponse> checks) {

    /** Teto documentado no contrato; hoje há uma verificação só (banco de dados). */
    static final int MAX_CHECKS = 16;

    ReadinessResponse {
        checks = List.copyOf(checks);
    }

    static ReadinessResponse of(List<CheckResponse> checks) {
        HealthStatus worst = checks.stream()
                .map(CheckResponse::status)
                .min(Comparator.naturalOrder())
                .orElse(HealthStatus.HEALTHY);
        return new ReadinessResponse(worst, checks);
    }
}
