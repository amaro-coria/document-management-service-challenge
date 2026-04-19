package com.clara.ops.challenge.document_management_service_challenge.domain.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "documents")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString(exclude = "tags")
public class DocumentEntity {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "user_name", nullable = false, length = 255)
  private String userName;

  @Column(name = "name", nullable = false, length = 512)
  private String name;

  @Column(name = "minio_path", nullable = false, length = 1024)
  private String minioPath;

  @Column(name = "size_bytes")
  private Long sizeBytes;

  @Column(name = "content_type", length = 127)
  private String contentType;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 32)
  private DocumentStatus status;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @ElementCollection(fetch = FetchType.LAZY)
  @CollectionTable(
      name = "document_tags",
      joinColumns = @JoinColumn(name = "document_id", nullable = false))
  @Column(name = "tag", nullable = false, length = 128)
  @Builder.Default
  private Set<String> tags = new HashSet<>();
}
