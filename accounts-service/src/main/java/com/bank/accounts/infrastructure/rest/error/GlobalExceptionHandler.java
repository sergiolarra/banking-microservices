package com.bank.accounts.infrastructure.rest.error;

import com.bank.accounts.domain.exception.DomainException;
import com.bank.accounts.domain.exception.ErrorCategory;
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
 * errors keep the status Spring assigns and get a catalog code and an English title (ADR-027).
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
                "An unexpected error occurred. Please try again later.");
        return respond(problem, "INTERNAL_ERROR", "Internal error", new HttpHeaders(), request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldErrorDetail> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorDetail(error.getField(), error.getDefaultMessage()))
                .sorted(Comparator.comparing(FieldErrorDetail::field).thenComparing(FieldErrorDetail::message))
                .toList();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "The request contains invalid data");
        problem.setProperty("errors", errors);
        return respond(problem, "VALIDATION_ERROR", "Invalid input", headers, request);
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
                "One or more parameters have invalid values");
        problem.setProperty("errors", errors);
        return respond(problem, "INVALID_PARAMETER", "Invalid parameter", headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Parameter '%s' has an invalid value".formatted(ex.getPropertyName()));
        return respond(problem, "INVALID_PARAMETER", "Invalid parameter", headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex, HttpHeaders headers, HttpStatusCode status,
            WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Required parameter '%s' is missing".formatted(ex.getParameterName()));
        return respond(problem, "INVALID_PARAMETER", "Invalid parameter", headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "The request body is not valid JSON or contains values that are not allowed");
        return respond(problem, "MALFORMED_JSON", "Malformed request body", headers, request);
    }

    /** Remaining Spring MVC errors (404 route, 405, 415…): keep their status, add code and title. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = body instanceof ProblemDetail detail ? detail : ProblemDetail.forStatus(status);
        return switch (status.value()) {
            case 404 -> respond(withDetail(problem, "The requested resource does not exist"),
                    "RESOURCE_NOT_FOUND", "Resource not found", headers, request);
            case 405 -> respond(withDetail(problem, "The HTTP method is not allowed on this resource"),
                    "METHOD_NOT_ALLOWED", "Method not allowed", headers, request);
            case 415 -> respond(withDetail(problem, "The content type is not supported; use application/json"),
                    "UNSUPPORTED_MEDIA_TYPE", "Unsupported media type", headers, request);
            default -> respond(problem, codeOf(status), "Request error", headers, request);
        };
    }

    static HttpStatus statusOf(ErrorCategory category) {
        return switch (category) {
            case INVALID_INPUT -> HttpStatus.BAD_REQUEST;
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
