package com.bank.clients.infrastructure.rest.error;

import com.bank.clients.domain.exception.DomainException;
import com.bank.clients.domain.exception.ErrorCategory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The single place where errors become RFC 7807 responses (ADR-014, SPEC §8).
 * Domain errors are mapped through their {@link ErrorCategory} (ADR-026); Spring MVC
 * errors keep the status Spring assigns and get a catalog code and a Spanish title.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String TYPE_BASE = "https://bank.local/errors/";

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(DomainException.class)
    ResponseEntity<Object> handleDomainException(DomainException ex, WebRequest request) {
        HttpStatus status = statusOf(ex.category());
        return respond(ProblemDetail.forStatusAndDetail(status, ex.getMessage()), ex.code(), ex.title(),
                new HttpHeaders(), request);
    }

    /** Last resort: logs the cause and answers without technical details (principle 8). */
    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        log.error("Unexpected error", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocurrió un error inesperado. Intente nuevamente más tarde.");
        return respond(problem, "INTERNAL_ERROR", "Error interno", new HttpHeaders(), request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldErrorDetail> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorDetail(error.getField(), error.getDefaultMessage()))
                .sorted(Comparator.comparing(FieldErrorDetail::field).thenComparing(FieldErrorDetail::message))
                .toList();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "La solicitud contiene datos inválidos");
        problem.setProperty("errors", errors);
        return respond(problem, "VALIDATION_ERROR", "Datos de entrada inválidos", headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldErrorDetail> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldErrorDetail(result.getMethodParameter().getParameterName(),
                                error.getDefaultMessage())))
                .toList();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Uno o más parámetros tienen valores inválidos");
        problem.setProperty("errors", errors);
        return respond(problem, "INVALID_PARAMETER", "Parámetro inválido", headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "El parámetro '%s' tiene un valor inválido".formatted(ex.getPropertyName()));
        return respond(problem, "INVALID_PARAMETER", "Parámetro inválido", headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex, HttpHeaders headers, HttpStatusCode status,
            WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Falta el parámetro obligatorio '%s'".formatted(ex.getParameterName()));
        return respond(problem, "INVALID_PARAMETER", "Parámetro inválido", headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "El cuerpo de la solicitud no es un JSON válido o contiene valores no permitidos");
        return respond(problem, "MALFORMED_JSON", "Cuerpo de la solicitud inválido", headers, request);
    }

    /** Remaining Spring MVC errors (404 route, 405, 415…): keep their status, add code and title. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = body instanceof ProblemDetail detail ? detail : ProblemDetail.forStatus(status);
        return switch (status.value()) {
            case 404 -> respond(withDetail(problem, "No existe el recurso solicitado"),
                    "RESOURCE_NOT_FOUND", "Recurso no encontrado", headers, request);
            case 405 -> respond(withDetail(problem, "El método HTTP no está permitido en este recurso"),
                    "METHOD_NOT_ALLOWED", "Método no permitido", headers, request);
            case 415 -> respond(withDetail(problem, "El tipo de contenido no está soportado; use application/json"),
                    "UNSUPPORTED_MEDIA_TYPE", "Tipo de contenido no soportado", headers, request);
            default -> respond(problem, codeOf(status), "Error en la solicitud", headers, request);
        };
    }

    static HttpStatus statusOf(ErrorCategory category) {
        return switch (category) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case BUSINESS_RULE -> HttpStatus.UNPROCESSABLE_CONTENT;
        };
    }

    private ResponseEntity<Object> respond(
            ProblemDetail problem, String code, String title, HttpHeaders headers, WebRequest request) {
        problem.setType(URI.create(TYPE_BASE + code.toLowerCase(Locale.ROOT).replace('_', '-')));
        problem.setTitle(title);
        problem.setProperty("code", code);
        problem.setProperty("timestamp", clock.instant());
        if (request instanceof ServletWebRequest servletRequest) {
            problem.setInstance(URI.create(servletRequest.getRequest().getRequestURI()));
        }
        return ResponseEntity.status(problem.getStatus()).headers(headers).body(problem);
    }

    private static ProblemDetail withDetail(ProblemDetail problem, String detail) {
        problem.setDetail(detail);
        return problem;
    }

    private static String codeOf(HttpStatusCode status) {
        HttpStatus known = HttpStatus.resolve(status.value());
        return known != null ? known.name() : "HTTP_" + status.value();
    }
}
