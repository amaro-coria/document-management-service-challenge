package com.clara.ops.challenge.document_management_service_challenge.domain.service;

import com.clara.ops.challenge.document_management_service_challenge.api.dto.DocumentDownloadUrl;
import com.clara.ops.challenge.document_management_service_challenge.api.dto.DocumentSearchFilters;
import com.clara.ops.challenge.document_management_service_challenge.api.dto.PaginatedDocumentSearch;
import com.clara.ops.challenge.document_management_service_challenge.api.dto.UploadCompleteResponse;
import com.clara.ops.challenge.document_management_service_challenge.api.dto.UploadDocumentRequest;
import com.clara.ops.challenge.document_management_service_challenge.api.dto.UploadDocumentResponse;
import com.clara.ops.challenge.document_management_service_challenge.common.exception.DocumentNotFoundException;
import com.clara.ops.challenge.document_management_service_challenge.common.exception.UploadNotReadyException;
import com.clara.ops.challenge.document_management_service_challenge.common.mapper.DocumentMapper;
import com.clara.ops.challenge.document_management_service_challenge.domain.model.DocumentEntity;
import com.clara.ops.challenge.document_management_service_challenge.domain.model.DocumentStatus;
import com.clara.ops.challenge.document_management_service_challenge.domain.repo.DocumentRepository;
import com.clara.ops.challenge.document_management_service_challenge.domain.repo.DocumentSpecifications;
import com.clara.ops.challenge.document_management_service_challenge.storage.MinioProperties;
import com.clara.ops.challenge.document_management_service_challenge.storage.StoragePort;
import com.clara.ops.challenge.document_management_service_challenge.storage.StoragePort.ObjectStat;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

  private final DocumentRepository repository;
  private final StoragePort storage;
  private final MinioProperties storageProps;

  /**
   * Creates a PENDING metadata row and returns a presigned PUT URL. The client PUTs the PDF bytes
   * directly to MinIO (the service never buffers the payload), then calls
   * {@link #completeUpload(UUID)} to mark the document available.
   */
  @Transactional
  public UploadDocumentResponse initUpload(UploadDocumentRequest request) {
    UUID id = UUID.randomUUID();
    String objectKey = storage.buildObjectKey(request.user(), request.name());

    DocumentEntity entity =
        DocumentEntity.builder()
            .id(id)
            .userName(request.user())
            .name(request.name())
            .minioPath(objectKey)
            .status(DocumentStatus.PENDING)
            .createdAt(OffsetDateTime.now())
            .tags(new HashSet<>(request.tags()))
            .build();
    repository.save(entity);

    Duration ttl = Duration.ofMinutes(storageProps.presignPutTtlMinutes());
    String url = storage.presignPut(objectKey, ttl);
    OffsetDateTime expiresAt = OffsetDateTime.now().plus(ttl);

    log.info("Initiated upload id={} user={} name={}", id, request.user(), request.name());
    return new UploadDocumentResponse(id, url, expiresAt);
  }

  /**
   * Verifies that the object has been uploaded to MinIO, records size/content-type, and flips
   * status to AVAILABLE.
   */
  @Transactional
  public UploadCompleteResponse completeUpload(UUID id) {
    DocumentEntity entity =
        repository.findById(id).orElseThrow(() -> new DocumentNotFoundException(id));

    Optional<ObjectStat> stat = storage.stat(entity.getMinioPath());
    if (stat.isEmpty()) {
      throw new UploadNotReadyException(id);
    }

    entity.setSizeBytes(stat.get().sizeBytes());
    entity.setContentType(stat.get().contentType());
    entity.setStatus(DocumentStatus.AVAILABLE);
    repository.save(entity);

    log.info(
        "Completed upload id={} size={}B type={}",
        id,
        entity.getSizeBytes(),
        entity.getContentType());
    return new UploadCompleteResponse(
        id, entity.getStatus(), entity.getSizeBytes(), entity.getContentType());
  }

  /**
   * Searches AVAILABLE documents with optional filters. When the caller does not specify a sort
   * the default is {@code createdAt DESC} per the challenge spec.
   */
  @Transactional(readOnly = true)
  public PaginatedDocumentSearch search(DocumentSearchFilters filters, Pageable pageable) {
    DocumentSearchFilters f = filters == null ? new DocumentSearchFilters(null, null, null) : filters;

    Specification<DocumentEntity> spec =
        Specification.where(DocumentSpecifications.hasStatus(DocumentStatus.AVAILABLE))
            .and(DocumentSpecifications.hasUser(f.user()))
            .and(DocumentSpecifications.nameContains(f.name()))
            .and(DocumentSpecifications.hasAnyTag(f.tags()));

    Pageable effective =
        pageable.getSort().isSorted()
            ? pageable
            : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "createdAt"));

    Page<DocumentEntity> page = repository.findAll(spec, effective);
    return DocumentMapper.toPaginated(page);
  }

  /**
   * Generates a time-limited presigned GET URL for an AVAILABLE document. Returns 404 (via
   * {@link DocumentNotFoundException}) if the document does not exist or has not completed
   * upload yet — we don't want to leak the existence of PENDING rows.
   */
  @Transactional(readOnly = true)
  public DocumentDownloadUrl getDownloadUrl(UUID id) {
    DocumentEntity entity =
        repository.findById(id).orElseThrow(() -> new DocumentNotFoundException(id));
    if (entity.getStatus() != DocumentStatus.AVAILABLE) {
      throw new DocumentNotFoundException(id);
    }
    Duration ttl = Duration.ofMinutes(storageProps.presignGetTtlMinutes());
    String url = storage.presignGet(entity.getMinioPath(), ttl);
    return new DocumentDownloadUrl(url);
  }
}
