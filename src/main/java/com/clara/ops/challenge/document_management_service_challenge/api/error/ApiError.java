package com.clara.ops.challenge.document_management_service_challenge.api.error;

import java.time.OffsetDateTime;
import java.util.List;

public record ApiError(
    OffsetDateTime timestamp,
    int status,
    String error,
    String message,
    String path,
    List<FieldViolation> violations) {

  public record FieldViolation(String field, String message) {}

  public static ApiError of(int status, String error, String message, String path) {
    return new ApiError(OffsetDateTime.now(), status, error, message, path, null);
  }

  public static ApiError validation(String message, String path, List<FieldViolation> violations) {
    return new ApiError(OffsetDateTime.now(), 400, "Bad Request", message, path, violations);
  }
}
