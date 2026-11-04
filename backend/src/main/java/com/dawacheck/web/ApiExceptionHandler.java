package com.dawacheck.web;

import com.dawacheck.dto.Dto;
import com.dawacheck.service.BadRequestException;
import com.dawacheck.service.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Turns every failure into the JSON shape api.js reads: {@code {message, status}}.
 *
 * The messages are written to be shown to a family, not to a developer, because
 * the frontend prints them directly. Nothing here echoes a stack trace, an SQL
 * fragment or an email address back to the browser.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<Dto.ApiError> notFound(NotFoundException e) {
    return of(HttpStatus.NOT_FOUND, e.getMessage());
  }

  @ExceptionHandler({BadRequestException.class, IllegalArgumentException.class})
  public ResponseEntity<Dto.ApiError> badRequest(Exception e) {
    return of(HttpStatus.BAD_REQUEST, e.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Dto.ApiError> invalid(MethodArgumentNotValidException e) {
    String message = e.getBindingResult().getFieldErrors().stream()
        .findFirst()
        .map(f -> f.getDefaultMessage() == null ? "That value is not valid."
            : f.getDefaultMessage())
        .orElse("Please check the form and try again.");
    return of(HttpStatus.BAD_REQUEST, message);
  }

  @ExceptionHandler({BadCredentialsException.class, AuthenticationException.class})
  public ResponseEntity<Dto.ApiError> auth(AuthenticationException e) {
    // Deliberately the same message for an unknown email and a wrong password.
    return of(HttpStatus.UNAUTHORIZED, "Email or password is not correct.");
  }

  /**
   * A missing file is a 404, not a server fault.
   *
   * Browsers ask for /favicon.ico whether or not one exists, so without this
   * every page load would log a stack trace and the real problems would be
   * harder to see.
   */
  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<Dto.ApiError> noResource(NoResourceFoundException e) {
    return of(HttpStatus.NOT_FOUND, "Not found.");
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<Dto.ApiError> tooBig(MaxUploadSizeExceededException e) {
    return of(HttpStatus.PAYLOAD_TOO_LARGE,
        "That photo is larger than 8 MB. Please take it again at a smaller size.");
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Dto.ApiError> unexpected(Exception e) {
    log.error("Unhandled failure while serving a request", e);
    return of(HttpStatus.INTERNAL_SERVER_ERROR,
        "Something went wrong on our side. Nothing was saved.");
  }

  private ResponseEntity<Dto.ApiError> of(HttpStatus status, String message) {
    return ResponseEntity.status(status).body(new Dto.ApiError(message, status.value()));
  }
}
