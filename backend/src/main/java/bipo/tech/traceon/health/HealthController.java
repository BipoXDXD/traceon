package bipo.tech.traceon.health;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Liveness e readiness (ADR 0004), anônimos por design: o orquestrador e o proxy consultam sem credencial. */
@RestController
@RequestMapping("/health")
@Tag(name = "Health", description = "Estado do processo da API e das dependências dele")
class HealthController {

    /** Parte do contrato de {@code /health/ready} lido pelo frontend. */
    static final String DATABASE_CHECK = "database";

    private final DatabaseProbe databaseProbe;

    HealthController(DatabaseProbe databaseProbe) {
        this.databaseProbe = databaseProbe;
    }

    @GetMapping("/live")
    @Operation(operationId = "getLiveness", summary = "Verifica se o processo da API está vivo", description = """
                    Não executa nenhuma verificação de dependência: uma falha do banco de dados não deve levar o \
                    orquestrador a reiniciar um processo saudável. Responde 200 enquanto o processo atende. Sem \
                    autenticação, para o orquestrador e o proxy; por isso o corpo é mínimo e não expõe diagnóstico \
                    (descrição, exceção, duração, host ou porta).""")
    @ApiResponse(
            responseCode = "200",
            description = "OK",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = LivenessResponse.class)))
    ResponseEntity<LivenessResponse> live() {
        return uncacheable(HttpStatus.OK, new LivenessResponse(HealthStatus.HEALTHY));
    }

    @GetMapping("/ready")
    @Operation(
            operationId = "getReadiness",
            summary = "Verifica se a API está pronta para receber tráfego",
            description = """
                    Executa as verificações de prontidão; hoje, abre uma conexão real com o banco de dados, limitada \
                    a 3 segundos. Responde 200 quando todas estão Healthy ou Degraded e 503 quando alguma está \
                    Unhealthy. Sem autenticação; o corpo traz só o status geral e, por verificação, nome e status, \
                    sem diagnóstico (descrição, exceção, duração, host, porta ou connection string).""")
    @ApiResponse(
            responseCode = "200",
            description = "OK",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ReadinessResponse.class)))
    @ApiResponse(
            responseCode = "503",
            description = "Service Unavailable",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ReadinessResponse.class)))
    ResponseEntity<ReadinessResponse> ready() {
        var readiness = ReadinessResponse.of(List.of(new CheckResponse(DATABASE_CHECK, databaseProbe.check())));
        HttpStatus status = readiness.status().servesTraffic() ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return uncacheable(status, readiness);
    }

    private static <T> ResponseEntity<T> uncacheable(HttpStatus status, T body) {
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .body(body);
    }
}
