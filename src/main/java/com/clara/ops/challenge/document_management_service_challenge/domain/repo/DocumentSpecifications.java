package com.clara.ops.challenge.document_management_service_challenge.domain.repo;

import com.clara.ops.challenge.document_management_service_challenge.domain.model.DocumentEntity;
import com.clara.ops.challenge.document_management_service_challenge.domain.model.DocumentStatus;
import jakarta.persistence.criteria.Join;
import java.util.Collection;
import org.springframework.data.jpa.domain.Specification;

/**
 * Builders for {@link DocumentEntity} search predicates. Each method returns {@code null} when the
 * filter is not applicable so callers can compose them with {@code Specification.where(...).and(...)}
 * without worrying about empty inputs.
 */
public final class DocumentSpecifications {

  private DocumentSpecifications() {}

  public static Specification<DocumentEntity> hasUser(String user) {
    if (user == null || user.isBlank()) {
      return null;
    }
    return (root, query, cb) -> cb.equal(root.get("userName"), user);
  }

  /** Case-insensitive substring match on document name. */
  public static Specification<DocumentEntity> nameContains(String name) {
    if (name == null || name.isBlank()) {
      return null;
    }
    String pattern = "%" + name.toLowerCase() + "%";
    return (root, query, cb) -> cb.like(cb.lower(root.get("name")), pattern);
  }

  /** Matches any document that has at least one of the provided tags. */
  public static Specification<DocumentEntity> hasAnyTag(Collection<String> tags) {
    if (tags == null || tags.isEmpty()) {
      return null;
    }
    return (root, query, cb) -> {
      if (query != null) {
        query.distinct(true);
      }
      Join<DocumentEntity, String> tagJoin = root.joinSet("tags");
      return tagJoin.in(tags);
    };
  }

  public static Specification<DocumentEntity> hasStatus(DocumentStatus status) {
    if (status == null) {
      return null;
    }
    return (root, query, cb) -> cb.equal(root.get("status"), status);
  }
}
