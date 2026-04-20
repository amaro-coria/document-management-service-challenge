package com.clara.ops.challenge.document_management_service_challenge.api.error;

import com.clara.ops.challenge.document_management_service_challenge.common.exception.DocumentNotFoundException;
import com.clara.ops.challenge.document_management_service_challenge.common.exception.StorageException;
import com.clara.ops.challenge.document_management_service_challenge.common.exception.UploadNotReadyException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(DocumentNotFoundException.class)
  public ResponseEntity<ApiError> handleNotFound(
      DocumentNotFoundException ex, HttpServletRequest req) {
    return build(HttpStatus.NOT_FOUND, ex.getMessage(), req);
  }

  @ExceptionHandler(UploadNotReadyException.class)
  public ResponseEntity<ApiError> handleConflict(
      UploadNotReadyException ex, HttpServletRequest req) {
    return build(HttpStatus.CONFLICT, ex.getMessage(), req);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiError> handleBeanValidation(
      MethodArgumentNotValidException ex, HttpServletRequest req) {
    List<ApiError.FieldViolation> violations =
        ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> new ApiError.FieldViolation(fe.getField(), fe.getDefaultMessage()))
            .toList();
    return ResponseEntity.badRequest()
        .body(ApiError.validation("Request validation failed", req.getRequestURI(), violations));
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiError> handleConstraintViolation(
      ConstraintViolationException ex, HttpServletRequest req) {
    List<ApiError.FieldViolation> violations =
        ex.getConstraintViolations().stream()
            .map(v -> new ApiError.FieldViolation(v.getPropertyPath().toString(), v.getMessage()))
            .toList();
    return ResponseEntity.badRequest()
        .body(ApiError.validation("Request validation failed", req.getRequestURI(), violations));
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    IllegalArgumentException.class
  })
  public ResponseEntity<ApiError> handleBadRequest(Exception ex, HttpServletRequest req) {
    return build(HttpStatus.BAD_REQUEST, ex.getMessage(), req);
  }

  @ExceptionHandler(StorageException.class)
  public ResponseEntity<ApiError> handleStorage(StorageException ex, HttpServletRequest req) {
    log.error("Storage failure on {}", req.getRequestURI(), ex);
    return build(HttpStatus.INTERNAL_SERVER_ERROR, "Storage backend failure", req);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest req) {
    log.error("Unhandled exception on {}", req.getRequestURI(), ex);
    return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", req);
  }

  private ResponseEntity<ApiError> build(
      HttpStatus status, String message, HttpServletRequest req) {
    return ResponseEntity.status(status)
        .body(ApiError.of(status.value(), status.getReasonPhrase(), message, req.getRequestURI()));
  }
}
