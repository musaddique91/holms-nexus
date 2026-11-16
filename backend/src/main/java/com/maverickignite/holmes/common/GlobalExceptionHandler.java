package com.maverickignite.holmes.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
/** Errors from the Intelligence API only; the MCP app has its own handler (McpExceptionHandler). */
@RestControllerAdvice(basePackages = "com.maverickignite.holmes")
public class GlobalExceptionHandler {

  @ExceptionHandler(NotFoundException.class)
  ProblemDetail notFound(NotFoundException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
  }

  @ExceptionHandler(BadRequestException.class)
  ProblemDetail badRequest(BadRequestException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ProblemDetail invalid(MethodArgumentNotValidException e) {
    String detail =
        e.getBindingResult().getFieldErrors().stream()
            .map(f -> f.getField() + ": " + f.getDefaultMessage())
            .reduce((a, b) -> a + "; " + b)
            .orElse("Validation failed");
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
  }
}
