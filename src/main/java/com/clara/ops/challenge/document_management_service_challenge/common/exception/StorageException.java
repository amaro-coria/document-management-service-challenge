package com.clara.ops.challenge.document_management_service_challenge.common.exception;

/** Raised when the object-store fails in an unrecoverable way. */
public class StorageException extends RuntimeException {
  public StorageException(String message, Throwable cause) {
    super(message, cause);
  }
}
