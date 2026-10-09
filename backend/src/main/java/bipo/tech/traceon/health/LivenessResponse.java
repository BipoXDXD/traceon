package bipo.tech.traceon.health;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Corpo de {@code /health/live}. Como todo o contrato de health, é uma allowlist: só nomes e estados saem do processo,
 * nunca descrição, exceção, duração ou dado que revele a conexão.
 */
record LivenessResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) HealthStatus status) {}
