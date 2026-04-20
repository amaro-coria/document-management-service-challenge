package com.clara.ops.challenge.document_management_service_challenge.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Strongly-typed binding for {@code storage.minio.*} in application.yml.
 *
 * <p>{@code endpoint} is the URL the service uses for admin/API calls (reachable from inside the
 * docker network). {@code externalEndpoint} is the URL baked into presigned URLs returned to
 * clients — it must be reachable from wherever the client lives. In local compose this is typically
 * {@code http://localhost:9000} vs. {@code http://minio:9000}. When unset, falls back to {@code
 * endpoint}.
 */
@ConfigurationProperties(prefix = "storage.minio")
public record MinioProperties(
    String endpoint,
    String externalEndpoint,
    String accessKey,
    String secretKey,
    String bucket,
    int presignPutTtlMinutes,
    int presignGetTtlMinutes) {

  public String signingEndpoint() {
    return externalEndpoint == null || externalEndpoint.isBlank() ? endpoint : externalEndpoint;
  }
}
