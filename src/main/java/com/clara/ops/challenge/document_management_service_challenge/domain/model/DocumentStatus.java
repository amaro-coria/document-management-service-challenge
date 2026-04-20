package com.clara.ops.challenge.document_management_service_challenge.domain.model;

/**
 * Lifecycle state of a document.
 *
 * <p>PENDING: metadata row exists; the client has been issued a presigned PUT URL but the object
 * has not yet been confirmed in MinIO.
 *
 * <p>AVAILABLE: the object has been verified in MinIO (size/type populated) and can be searched and
 * downloaded.
 */
public enum DocumentStatus {
  PENDING,
  AVAILABLE
}
