package com.clara.ops.challenge.document_management_service_challenge.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.clara.ops.challenge.document_management_service_challenge.api.dto.DocumentDownloadUrl;
import com.clara.ops.challenge.document_management_service_challenge.api.dto.DocumentSearchFilters;
import com.clara.ops.challenge.document_management_service_challenge.api.dto.PaginatedDocumentSearch;
import com.clara.ops.challenge.document_management_service_challenge.api.dto.UploadCompleteResponse;
import com.clara.ops.challenge.document_management_service_challenge.api.dto.UploadDocumentRequest;
import com.clara.ops.challenge.document_management_service_challenge.api.dto.UploadDocumentResponse;
import com.clara.ops.challenge.document_management_service_challenge.common.exception.DocumentNotFoundException;
import com.clara.ops.challenge.document_management_service_challenge.common.exception.UploadNotReadyException;
import com.clara.ops.challenge.document_management_service_challenge.domain.model.DocumentEntity;
import com.clara.ops.challenge.document_management_service_challenge.domain.model.DocumentStatus;
import com.clara.ops.challenge.document_management_service_challenge.domain.repo.DocumentRepository;
import com.clara.ops.challenge.document_management_service_challenge.storage.MinioProperties;
import com.clara.ops.challenge.document_management_service_challenge.storage.StoragePort;
import com.clara.ops.challenge.document_management_service_challenge.storage.StoragePort.ObjectStat;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

  @Mock DocumentRepository repository;
  @Mock StoragePort storage;
  MinioProperties props;

  @InjectMocks DocumentService service;

  @BeforeEach
  void setUp() {
    props =
        new MinioProperties(
            "http://localhost:9000", null, "us-east-1", "ak", "sk", "bucket", 15, 5);
    service = new DocumentService(repository, storage, props);
  }

  @Test
  void initUpload_persistsPendingRowAndReturnsPresignedUrl() {
    UploadDocumentRequest req = new UploadDocumentRequest("alice", "doc.pdf", Set.of("a", "b"));
    when(storage.buildObjectKey("alice", "doc.pdf")).thenReturn("alice/doc.pdf");
    when(storage.presignPut(eq("alice/doc.pdf"), any(Duration.class))).thenReturn("http://put");

    UploadDocumentResponse resp = service.initUpload(req);

    assertThat(resp.id()).isNotNull();
    assertThat(resp.uploadUrl()).isEqualTo("http://put");
    assertThat(resp.expiresAt()).isAfter(OffsetDateTime.now());

    ArgumentCaptor<DocumentEntity> captor = ArgumentCaptor.forClass(DocumentEntity.class);
    verify(repository).save(captor.capture());
    DocumentEntity saved = captor.getValue();
    assertThat(saved.getStatus()).isEqualTo(DocumentStatus.PENDING);
    assertThat(saved.getUserName()).isEqualTo("alice");
    assertThat(saved.getName()).isEqualTo("doc.pdf");
    assertThat(saved.getMinioPath()).isEqualTo("alice/doc.pdf");
    assertThat(saved.getTags()).containsExactlyInAnyOrder("a", "b");
  }

  @Test
  void completeUpload_marksAvailableAndRecordsStat() {
    UUID id = UUID.randomUUID();
    DocumentEntity entity = pendingEntity(id, "alice", "doc.pdf");
    when(repository.findById(id)).thenReturn(Optional.of(entity));
    when(storage.stat("alice/doc.pdf"))
        .thenReturn(Optional.of(new ObjectStat(1234L, "application/pdf")));

    UploadCompleteResponse resp = service.completeUpload(id);

    assertThat(resp.status()).isEqualTo(DocumentStatus.AVAILABLE);
    assertThat(resp.sizeBytes()).isEqualTo(1234L);
    assertThat(resp.contentType()).isEqualTo("application/pdf");
    assertThat(entity.getStatus()).isEqualTo(DocumentStatus.AVAILABLE);
    verify(repository).save(entity);
  }

  @Test
  void completeUpload_throwsNotFoundWhenMissing() {
    UUID id = UUID.randomUUID();
    when(repository.findById(id)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.completeUpload(id))
        .isInstanceOf(DocumentNotFoundException.class);
    verify(storage, never()).stat(anyString());
  }

  @Test
  void completeUpload_throwsConflictWhenObjectNotYetInStorage() {
    UUID id = UUID.randomUUID();
    DocumentEntity entity = pendingEntity(id, "alice", "doc.pdf");
    when(repository.findById(id)).thenReturn(Optional.of(entity));
    when(storage.stat("alice/doc.pdf")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.completeUpload(id))
        .isInstanceOf(UploadNotReadyException.class);
    verify(repository, never()).save(entity);
    assertThat(entity.getStatus()).isEqualTo(DocumentStatus.PENDING);
  }

  @Test
  void getDownloadUrl_returnsPresignedUrl() {
    UUID id = UUID.randomUUID();
    DocumentEntity entity = pendingEntity(id, "alice", "doc.pdf");
    entity.setStatus(DocumentStatus.AVAILABLE);
    when(repository.findById(id)).thenReturn(Optional.of(entity));
    when(storage.presignGet(eq("alice/doc.pdf"), any(Duration.class))).thenReturn("http://get");

    DocumentDownloadUrl result = service.getDownloadUrl(id);
    assertThat(result.url()).isEqualTo("http://get");
  }

  @Test
  void getDownloadUrl_throwsNotFoundWhenMissing() {
    UUID id = UUID.randomUUID();
    when(repository.findById(id)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.getDownloadUrl(id))
        .isInstanceOf(DocumentNotFoundException.class);
  }

  @Test
  void getDownloadUrl_throwsNotFoundWhenStillPending() {
    UUID id = UUID.randomUUID();
    DocumentEntity entity = pendingEntity(id, "alice", "doc.pdf");
    when(repository.findById(id)).thenReturn(Optional.of(entity));

    assertThatThrownBy(() -> service.getDownloadUrl(id))
        .isInstanceOf(DocumentNotFoundException.class);
    verify(storage, never()).presignGet(anyString(), any());
  }

  @Test
  void search_appliesDefaultSortWhenClientDoesNotSupplyOne() {
    DocumentSearchFilters filters = new DocumentSearchFilters(null, null, null);
    Pageable unsorted = PageRequest.of(0, 10);
    Page<DocumentEntity> page = new PageImpl<>(List.of());
    when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

    PaginatedDocumentSearch result = service.search(filters, unsorted);

    ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
    verify(repository).findAll(any(Specification.class), captor.capture());
    Sort actualSort = captor.getValue().getSort();
    assertThat(actualSort.getOrderFor("createdAt")).isNotNull();
    assertThat(actualSort.getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
    assertThat(result.metadata().currentPage()).isZero();
    assertThat(result.documents()).isEmpty();
  }

  @Test
  void search_preservesClientSuppliedSort() {
    DocumentSearchFilters filters = new DocumentSearchFilters("alice", null, null);
    Pageable sorted = PageRequest.of(1, 5, Sort.by("name").ascending());
    when(repository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of()));

    service.search(filters, sorted);

    ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
    verify(repository).findAll(any(Specification.class), captor.capture());
    assertThat(captor.getValue()).isSameAs(sorted);
  }

  private DocumentEntity pendingEntity(UUID id, String user, String name) {
    return DocumentEntity.builder()
        .id(id)
        .userName(user)
        .name(name)
        .minioPath(user + "/" + name)
        .status(DocumentStatus.PENDING)
        .createdAt(OffsetDateTime.now())
        .tags(new HashSet<>(Set.of("x")))
        .build();
  }
}
