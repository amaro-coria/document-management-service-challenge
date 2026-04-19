package com.clara.ops.challenge.document_management_service_challenge.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Strongly-typed binding for {@code storage.minio.*} in application.yml. */
@ConfigurationProperties(prefix = "storage.minio")
public record MinioProperties(
    String endpoint,
    String accessKey,
    String secretKey,
    String bucket,
    int presignPutTtlMinutes,
    int presignGetTtlMinutes) {}
