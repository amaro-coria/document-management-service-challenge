package com.clara.ops.challenge.document_management_service_challenge.common.mapper;

import com.clara.ops.challenge.document_management_service_challenge.api.dto.DocumentDto;
import com.clara.ops.challenge.document_management_service_challenge.api.dto.MetadataDto;
import com.clara.ops.challenge.document_management_service_challenge.api.dto.PaginatedDocumentSearch;
import com.clara.ops.challenge.document_management_service_challenge.domain.model.DocumentEntity;
import java.util.HashSet;
import java.util.List;
import org.springframework.data.domain.Page;

public final class DocumentMapper {

  private DocumentMapper() {}

  public static DocumentDto toDto(DocumentEntity entity) {
    return new DocumentDto(
        entity.getId(),
        entity.getUserName(),
        entity.getName(),
        new HashSet<>(entity.getTags()),
        entity.getSizeBytes(),
        entity.getContentType(),
        entity.getCreatedAt());
  }

  public static PaginatedDocumentSearch toPaginated(Page<DocumentEntity> page) {
    List<DocumentDto> docs = page.getContent().stream().map(DocumentMapper::toDto).toList();
    MetadataDto metadata =
        new MetadataDto(
            page.getNumber(), page.getSize(), docs.size(), page.getTotalPages(), page.getTotalElements());
    return new PaginatedDocumentSearch(metadata, docs);
  }
}
