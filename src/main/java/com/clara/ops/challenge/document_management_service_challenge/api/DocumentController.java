package com.clara.ops.challenge.document_management_service_challenge.api;

import com.clara.ops.challenge.document_management_service_challenge.api.dto.DocumentDownloadUrl;
import com.clara.ops.challenge.document_management_service_challenge.api.dto.DocumentSearchFilters;
import com.clara.ops.challenge.document_management_service_challenge.api.dto.PaginatedDocumentSearch;
import com.clara.ops.challenge.document_management_service_challenge.api.dto.UploadCompleteResponse;
import com.clara.ops.challenge.document_management_service_challenge.api.dto.UploadDocumentRequest;
import com.clara.ops.challenge.document_management_service_challenge.api.dto.UploadDocumentResponse;
import com.clara.ops.challenge.document_management_service_challenge.domain.service.DocumentService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/document-management")
@RequiredArgsConstructor
public class DocumentController {

  private final DocumentService service;

  @PostMapping("/upload")
  public ResponseEntity<UploadDocumentResponse> upload(
      @Valid @RequestBody UploadDocumentRequest request) {
    UploadDocumentResponse response = service.initUpload(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @PostMapping("/upload/{id}/complete")
  public ResponseEntity<UploadCompleteResponse> complete(@PathVariable("id") UUID id) {
    return ResponseEntity.ok(service.completeUpload(id));
  }

  @PostMapping("/search")
  public ResponseEntity<PaginatedDocumentSearch> search(
      @RequestBody(required = false) DocumentSearchFilters filters,
      @PageableDefault(size = 20) Pageable pageable) {
    return ResponseEntity.ok(service.search(filters, pageable));
  }

  @GetMapping("/download/{documentId}")
  public ResponseEntity<DocumentDownloadUrl> download(
      @PathVariable("documentId") UUID documentId) {
    return ResponseEntity.ok(service.getDownloadUrl(documentId));
  }
}
