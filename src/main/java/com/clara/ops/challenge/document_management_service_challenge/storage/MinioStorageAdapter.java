package com.clara.ops.challenge.document_management_service_challenge.storage;

import com.clara.ops.challenge.document_management_service_challenge.common.exception.StorageException;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MinioStorageAdapter implements StoragePort {

  private final MinioClient minioClient;
  private final MinioProperties properties;

  @PostConstruct
  void init() {
    try {
      ensureBucket();
    } catch (Exception ex) {
      // Don't prevent boot: the bootstrap sidecar normally handles bucket creation, and in local
      // dev we want the service to start even if MinIO is momentarily unreachable.
      log.warn(
          "Could not verify MinIO bucket '{}' at startup: {}", properties.bucket(), ex.getMessage());
    }
  }

  @Override
  public void ensureBucket() {
    try {
      boolean exists =
          minioClient.bucketExists(BucketExistsArgs.builder().bucket(properties.bucket()).build());
      if (!exists) {
        minioClient.makeBucket(MakeBucketArgs.builder().bucket(properties.bucket()).build());
        log.info("Created MinIO bucket '{}'", properties.bucket());
      }
    } catch (Exception ex) {
      throw new StorageException("Failed to ensure bucket " + properties.bucket(), ex);
    }
  }

  @Override
  public String buildObjectKey(String userName, String documentName) {
    return userName + "/" + documentName;
  }

  @Override
  public String presignPut(String objectKey, Duration ttl) {
    return presign(Method.PUT, objectKey, ttl);
  }

  @Override
  public String presignGet(String objectKey, Duration ttl) {
    return presign(Method.GET, objectKey, ttl);
  }

  @Override
  public Optional<ObjectStat> stat(String objectKey) {
    try {
      StatObjectResponse resp =
          minioClient.statObject(
              StatObjectArgs.builder().bucket(properties.bucket()).object(objectKey).build());
      return Optional.of(new ObjectStat(resp.size(), resp.contentType()));
    } catch (ErrorResponseException ex) {
      if ("NoSuchKey".equals(ex.errorResponse().code())) {
        return Optional.empty();
      }
      throw new StorageException("Failed to stat object " + objectKey, ex);
    } catch (Exception ex) {
      throw new StorageException("Failed to stat object " + objectKey, ex);
    }
  }

  private String presign(Method method, String objectKey, Duration ttl) {
    try {
      return minioClient.getPresignedObjectUrl(
          GetPresignedObjectUrlArgs.builder()
              .method(method)
              .bucket(properties.bucket())
              .object(objectKey)
              .expiry((int) ttl.toSeconds(), TimeUnit.SECONDS)
              .build());
    } catch (Exception ex) {
      throw new StorageException(
          "Failed to presign " + method + " URL for " + objectKey, ex);
    }
  }
}
