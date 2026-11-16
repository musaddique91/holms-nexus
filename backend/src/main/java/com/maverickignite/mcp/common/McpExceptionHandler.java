package com.maverickignite.mcp.common;

import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Errors from the MCP API only; Intelligence has its own handler. */
@RestControllerAdvice(basePackages = "com.maverickignite.mcp")
public class McpExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(McpExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ProblemDetail handleApi(ApiException ex) {
        return ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + " " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    ProblemDetail handleConflict(Exception ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "A record with the same name already exists");
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    ProblemDetail handleNotFound(Exception ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Not found");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleOther(Exception ex) {
        log.error("Unhandled error", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
    }
}
