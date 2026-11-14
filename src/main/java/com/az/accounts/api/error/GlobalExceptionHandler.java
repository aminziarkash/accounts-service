package com.az.accounts.api.error;

import com.az.accounts.exception.AccountNotFoundException;
import com.az.accounts.exception.CustomerNotFoundException;
import com.az.accounts.exception.DuplicateEmailException;
import com.az.accounts.exception.IdempotencyKeyReuseException;
import com.az.accounts.exception.InsufficientFundsException;
import com.az.accounts.exception.InvalidOperationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }

    // 400: invalid fields. The parent class already handles broken JSON, wrong media type etc.
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ProblemDetail problem =
                problem(
                        HttpStatus.BAD_REQUEST,
                        "Validation failed",
                        "The request has invalid fields");
        List<Map<String, String>> errors =
                ex.getBindingResult().getFieldErrors().stream()
                        .map(e ->
                                Map.of(
                                        "field", e.getField(),
                                        "message", String.valueOf(e.getDefaultMessage())))
                        .toList();
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    // 400: the same answer when Spring validates the whole method (a header or request param has a
    // constraint, so the body is validated by method validation too)
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ProblemDetail problem =
                problem(
                        HttpStatus.BAD_REQUEST,
                        "Validation failed",
                        "The request has invalid fields");
        List<Map<String, String>> errors = new ArrayList<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            if (result instanceof ParameterErrors parameterErrors) {
                parameterErrors
                        .getFieldErrors()
                        .forEach(e ->
                                errors.add(
                                        Map.of(
                                                "field", e.getField(),
                                                "message", String.valueOf(e.getDefaultMessage()))));
            } else {
                String name = parameterName(result);
                result.getResolvableErrors()
                        .forEach(e ->
                                errors.add(
                                        Map.of(
                                                "field",
                                                name,
                                                "message",
                                                String.valueOf(e.getDefaultMessage()))));
            }
        }
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    private static String parameterName(ParameterValidationResult result) {
        RequestHeader header =
                result.getMethodParameter().getParameterAnnotation(RequestHeader.class);
        if (header != null && !header.value().isBlank()) {
            return header.value();
        }
        String name = result.getMethodParameter().getParameterName();
        return name != null ? name : "parameter";
    }

    // 401: no token, or a token that is invalid or expired
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleUnauthenticated(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .body(
                        problem(
                                HttpStatus.UNAUTHORIZED,
                                "Unauthorized",
                                "A valid access token is required"));
    }

    // 403: a valid token, but not allowed to do this
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleForbidden(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, "Forbidden", "You are not allowed to do this");
    }

    // 404
    @ExceptionHandler({AccountNotFoundException.class, CustomerNotFoundException.class})
    public ProblemDetail handleNotFound(RuntimeException ex) {
        return problem(HttpStatus.NOT_FOUND, "Resource not found", ex.getMessage());
    }

    // 409
    @ExceptionHandler(DuplicateEmailException.class)
    public ProblemDetail handleDuplicateEmail(DuplicateEmailException ex) {
        return problem(HttpStatus.CONFLICT, "Duplicate resource", ex.getMessage());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return problem(
                HttpStatus.CONFLICT,
                "Concurrent modification",
                "The resource was changed by someone else. Reload it and try again.");
    }

    // 503: the row lock could not be had in time (or we lost a deadlock). Nothing was changed, so
    // the client can safely retry.
    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleLockContention(
            PessimisticLockingFailureException ex) {
        log.warn("Lock contention", ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "1")
                .body(
                        problem(
                                HttpStatus.SERVICE_UNAVAILABLE,
                                "Temporarily busy",
                                "The account is busy. Try again shortly."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation", ex);
        return problem(HttpStatus.CONFLICT, "Conflict", "The request conflicts with existing data");
    }

    // 422: well-formed request, but a business rule says no
    @ExceptionHandler({
        InsufficientFundsException.class,
        InvalidOperationException.class,
        IdempotencyKeyReuseException.class
    })
    public ProblemDetail handleBusinessRule(RuntimeException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Business rule violated", ex.getMessage());
    }

    // 500: our bug. Log everything, tell the client nothing.
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal server error",
                "An unexpected error occurred");
    }
}
