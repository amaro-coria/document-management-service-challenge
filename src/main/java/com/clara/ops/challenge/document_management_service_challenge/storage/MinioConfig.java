package com.clara.ops.challenge.document_management_service_challenge.storage;

import io.minio.MinioClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MinioProperties.class)
public class MinioConfig {

  /** Client used for real API calls (bucket ops, statObject). Points at the internal endpoint. */
  @Bean
  public MinioClient minioAdminClient(MinioProperties props) {
    return MinioClient.builder()
        .endpoint(props.endpoint())
        .region(props.effectiveRegion())
        .credentials(props.accessKey(), props.secretKey())
        .build();
  }

  /**
   * Client used solely to generate presigned URLs. Presigning is offline signing, so the endpoint
   * here dictates the host baked into the URL handed to the client — it must match what the client
   * can reach.
   */
  @Bean
  public MinioClient minioSigningClient(MinioProperties props) {
    // Pin the region so presigning is fully offline. Without this, the SDK issues a
    // getBucketRegion probe against the endpoint — which is external and unreachable from
    // inside the service container.
    return MinioClient.builder()
        .endpoint(props.signingEndpoint())
        .region(props.effectiveRegion())
        .credentials(props.accessKey(), props.secretKey())
        .build();
  }
}
