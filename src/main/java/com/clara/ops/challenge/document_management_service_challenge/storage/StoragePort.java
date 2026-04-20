package com.clara.ops.challenge.document_management_service_challenge.storage;

import java.time.Duration;
import java.util.Optional;

/**
 * Abstraction over the object-store. Keeping this interface lets us mock it in unit tests and swap
 * the implementation (e.g. AWS S3) without touching the service layer.
 */
public interface StoragePort {

  /** Creates the bucket if it does not exist. Safe to call at startup. */
  void ensureBucket();

  /** Builds the canonical object key for a user's document: {@code {user}/{name}}. */
  String buildObjectKey(String userName, String documentName);

  /** Generates a time-limited presigned URL the client can PUT the object bytes to. */
  String presignPut(String objectKey, Duration ttl);

  /** Generates a time-limited presigned URL the client can GET the object bytes from. */
  String presignGet(String objectKey, Duration ttl);

  /** Returns object metadata (size + content-type) if the object exists, empty otherwise. */
  Optional<ObjectStat> stat(String objectKey);

  record ObjectStat(long sizeBytes, String contentType) {}
}
