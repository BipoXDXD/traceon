package bipo.tech.traceon.shared.web;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Responde em Problem Details o que o Tomcat encaminha para {@code /error} (exceção não tratada, {@code sendError}),
 * como o resto da API. Substitui o {@code BasicErrorController} do Boot, que usa outro formato. Só status, título e o
 * trace id: a causa fica no log, na linha com o mesmo id.
 */
@RestController
class ProblemDetailErrorController implements ErrorController {

    private static final String TRACE_ID_PROPERTY = "traceId";

    @RequestMapping("${server.error.path:/error}")
    ResponseEntity<ProblemDetail> error(HttpServletRequest request) {
        HttpStatus status = statusOf(request);
        ProblemDetail problem = ProblemDetail.forStatus(status);
        if (request.getAttribute(UnhandledExceptionLoggingFilter.TRACE_ID_ATTRIBUTE) instanceof String traceId) {
            problem.setProperty(TRACE_ID_PROPERTY, traceId);
        }
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    /** Sem encaminhamento do Tomcat, quem chamou foi o cliente, direto em /error: para ele a rota não existe. */
    private static HttpStatus statusOf(HttpServletRequest request) {
        if (!(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) instanceof Integer code)) {
            return HttpStatus.NOT_FOUND;
        }
        HttpStatus status = HttpStatus.resolve(code);
        return status != null && status.isError() ? status : HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
