package bipo.tech.traceon.health;

import io.swagger.v3.oas.annotations.media.Schema;

/** Uma verificação de prontidão: o nome é constante do código ("database"), nunca dado. */
record CheckResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = MAX_NAME_LENGTH, pattern = NAME_PATTERN)
        String name,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) HealthStatus status) {

    static final int MAX_NAME_LENGTH = 64;
    static final String NAME_PATTERN = "^[a-z][a-z0-9-]*$";
}
