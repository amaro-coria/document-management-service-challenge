package com.clara.ops.challenge.document_management_service_challenge.api.dto;

import com.clara.ops.challenge.document_management_service_challenge.domain.model.DocumentStatus;
import java.util.UUID;

public record UploadCompleteResponse(UUID id, DocumentStatus status, long sizeBytes, String contentType) {}
