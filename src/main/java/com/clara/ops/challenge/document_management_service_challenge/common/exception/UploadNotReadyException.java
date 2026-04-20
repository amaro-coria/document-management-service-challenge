package com.clara.ops.challenge.document_management_service_challenge.common.exception;

import java.util.UUID;

/**
 * Raised when a client calls the "complete" endpoint but the object has not yet been uploaded to
 * MinIO (presigned PUT not executed or still in flight). Maps to HTTP 409 Conflict.
 */
public class UploadNotReadyException extends RuntimeException {
  public UploadNotReadyException(UUID id) {
    super("Object for document " + id + " has not been uploaded yet");
  }
}
