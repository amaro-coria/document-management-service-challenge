package com.clara.ops.challenge.document_management_service_challenge.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.clara.ops.challenge.document_management_service_challenge.api.dto.DocumentDownloadUrl;
import com.clara.ops.challenge.document_management_service_challenge.api.dto.MetadataDto;
import com.clara.ops.challenge.document_management_service_challenge.api.dto.PaginatedDocumentSearch;
import com.clara.ops.challenge.document_management_service_challenge.api.dto.UploadCompleteResponse;
import com.clara.ops.challenge.document_management_service_challenge.api.dto.UploadDocumentRequest;
import com.clara.ops.challenge.document_management_service_challenge.api.dto.UploadDocumentResponse;
import com.clara.ops.challenge.document_management_service_challenge.api.error.GlobalExceptionHandler;
import com.clara.ops.challenge.document_management_service_challenge.api.filter.RequestIdFilter;
import com.clara.ops.challenge.document_management_service_challenge.common.exception.DocumentNotFoundException;
import com.clara.ops.challenge.document_management_service_challenge.common.exception.UploadNotReadyException;
import com.clara.ops.challenge.document_management_service_challenge.domain.model.DocumentStatus;
import com.clara.ops.challenge.document_management_service_challenge.domain.service.DocumentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = DocumentController.class)
@Import({GlobalExceptionHandler.class, RequestIdFilter.class})
class DocumentControllerTest {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper objectMapper;
  @MockBean DocumentService service;

  @Test
  void upload_returns201AndBody() throws Exception {
    UUID id = UUID.randomUUID();
    OffsetDateTime expiresAt = OffsetDateTime.now().plusMinutes(15);
    when(service.initUpload(any()))
        .thenReturn(new UploadDocumentResponse(id, "http://put", expiresAt));

    String body =
        objectMapper.writeValueAsString(
            new UploadDocumentRequest("alice", "doc.pdf", java.util.Set.of("tag1")));

    mvc.perform(
            post("/document-management/upload")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(id.toString()))
        .andExpect(jsonPath("$.uploadUrl").value("http://put"))
        .andExpect(header().exists("X-Request-Id"));
  }

  @Test
  void upload_returns400WhenValidationFails() throws Exception {
    String body =
        objectMapper.writeValueAsString(
            new UploadDocumentRequest("", "doc.pdf", java.util.Set.of()));

    mvc.perform(
            post("/document-management/upload")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.violations").isArray());
  }

  @Test
  void complete_returns409WhenNotReady() throws Exception {
    UUID id = UUID.randomUUID();
    when(service.completeUpload(id)).thenThrow(new UploadNotReadyException(id));

    mvc.perform(post("/document-management/upload/{id}/complete", id))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409));
  }

  @Test
  void complete_returns200WhenAvailable() throws Exception {
    UUID id = UUID.randomUUID();
    when(service.completeUpload(id))
        .thenReturn(
            new UploadCompleteResponse(id, DocumentStatus.AVAILABLE, 42L, "application/pdf"));

    mvc.perform(post("/document-management/upload/{id}/complete", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("AVAILABLE"))
        .andExpect(jsonPath("$.sizeBytes").value(42));
  }

  @Test
  void download_returns404WhenMissing() throws Exception {
    UUID id = UUID.randomUUID();
    when(service.getDownloadUrl(id)).thenThrow(new DocumentNotFoundException(id));

    mvc.perform(get("/document-management/download/{id}", id))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404));
  }

  @Test
  void download_returns200WithUrl() throws Exception {
    UUID id = UUID.randomUUID();
    when(service.getDownloadUrl(id)).thenReturn(new DocumentDownloadUrl("http://get"));

    mvc.perform(get("/document-management/download/{id}", id))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"url\":\"http://get\"}"));
  }

  @Test
  void search_returns200WithEmptyPage() throws Exception {
    when(service.search(any(), any(Pageable.class)))
        .thenReturn(new PaginatedDocumentSearch(new MetadataDto(0, 20, 0, 0, 0), List.of()));

    mvc.perform(
            post("/document-management/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.metadata.totalItems").value(0))
        .andExpect(jsonPath("$.documents").isArray());
  }
}
