package com.clara.ops.challenge.document_management_service_challenge.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.clara.ops.challenge.document_management_service_challenge.api.dto.UploadDocumentResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * End-to-end flow against real Postgres and MinIO containers: init upload → client PUTs bytes to
 * the presigned URL → complete → search → download → GET the document. Exercises persistence,
 * storage integration, and controller wiring together.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class DocumentManagementIntegrationTest {

  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
          .withDatabaseName("challenge")
          .withUsername("challenge_user")
          .withPassword("challenge_password")
          .withInitScript("schema-init-test.sql");

  @Container
  static MinIOContainer minio =
      new MinIOContainer(DockerImageName.parse("minio/minio:latest"))
          .withUserName("minio_admin")
          .withPassword("minio_admin_password");

  @DynamicPropertySource
  static void overrideProps(DynamicPropertyRegistry reg) {
    reg.add("spring.datasource.url", postgres::getJdbcUrl);
    reg.add("spring.datasource.username", postgres::getUsername);
    reg.add("spring.datasource.password", postgres::getPassword);
    reg.add("spring.jpa.properties.hibernate.default_schema", () -> "document_schema");
    reg.add("storage.minio.endpoint", minio::getS3URL);
    reg.add("storage.minio.access-key", minio::getUserName);
    reg.add("storage.minio.secret-key", minio::getPassword);
    reg.add("storage.minio.bucket", () -> "document-bucket");
    reg.add("storage.minio.presign-put-ttl-minutes", () -> 5);
    reg.add("storage.minio.presign-get-ttl-minutes", () -> 5);
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper objectMapper;

  @Test
  void fullUploadSearchDownloadFlow() throws Exception {
    // 1. init upload
    String initBody =
        objectMapper.writeValueAsString(
            Map.of("user", "alice", "name", "doc-it.pdf", "tags", Set.of("it", "smoke")));

    MvcResult initResult =
        mvc.perform(
                post("/document-management/upload")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(initBody))
            .andExpect(status().isCreated())
            .andReturn();

    UploadDocumentResponse init =
        objectMapper.readValue(
            initResult.getResponse().getContentAsString(), UploadDocumentResponse.class);

    // 2. client PUTs bytes straight to MinIO
    int putStatus = putBytesToUrl(init.uploadUrl(), "%PDF-1.4 fake content".getBytes());
    assertThat(putStatus).isBetween(200, 299);

    // 3. complete
    mvc.perform(post("/document-management/upload/{id}/complete", init.id()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("AVAILABLE"))
        .andExpect(jsonPath("$.sizeBytes").value(20));

    // 4. search finds it and excludes PENDING
    mvc.perform(
            post("/document-management/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"user\":\"alice\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.metadata.totalItems").value(1))
        .andExpect(jsonPath("$.documents[0].id").value(init.id().toString()))
        .andExpect(jsonPath("$.documents[0].tags", org.hamcrest.Matchers.hasSize(2)));

    // 5. download returns a usable presigned GET
    MvcResult dl =
        mvc.perform(get("/document-management/download/{id}", init.id()))
            .andExpect(status().isOk())
            .andReturn();
    String downloadUrl =
        objectMapper.readTree(dl.getResponse().getContentAsString()).get("url").asText();
    assertThat(downloadUrl).startsWith(minio.getS3URL());

    HttpURLConnection conn = (HttpURLConnection) URI.create(downloadUrl).toURL().openConnection();
    conn.setRequestMethod("GET");
    assertThat(conn.getResponseCode()).isEqualTo(200);
    byte[] downloaded = conn.getInputStream().readAllBytes();
    assertThat(new String(downloaded)).isEqualTo("%PDF-1.4 fake content");
  }

  @Test
  void completeReturns409WhenObjectMissing() throws Exception {
    String initBody =
        objectMapper.writeValueAsString(
            Map.of("user", "bob", "name", "missing.pdf", "tags", Set.of("t")));
    MvcResult initResult =
        mvc.perform(
                post("/document-management/upload")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(initBody))
            .andExpect(status().isCreated())
            .andReturn();
    UploadDocumentResponse init =
        objectMapper.readValue(
            initResult.getResponse().getContentAsString(), UploadDocumentResponse.class);

    // skip the PUT → object doesn't exist in MinIO yet
    mvc.perform(post("/document-management/upload/{id}/complete", init.id()))
        .andExpect(status().isConflict());
  }

  private static int putBytesToUrl(String url, byte[] payload) throws Exception {
    HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
    conn.setDoOutput(true);
    conn.setRequestMethod("PUT");
    conn.setRequestProperty("Content-Type", "application/pdf");
    conn.getOutputStream().write(payload);
    conn.getOutputStream().close();
    return conn.getResponseCode();
  }
}
