package com.clara.ops.challenge.document_management_service_challenge.domain.repo;

import com.clara.ops.challenge.document_management_service_challenge.domain.model.DocumentEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DocumentRepository
    extends JpaRepository<DocumentEntity, UUID>, JpaSpecificationExecutor<DocumentEntity> {}
