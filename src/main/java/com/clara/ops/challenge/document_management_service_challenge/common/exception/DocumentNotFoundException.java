package com.clara.ops.challenge.document_management_service_challenge.common.exception;

import java.util.UUID;

public class DocumentNotFoundException extends RuntimeException {
  public DocumentNotFoundException(UUID id) {
    super("Document not found: " + id);
  }
}
