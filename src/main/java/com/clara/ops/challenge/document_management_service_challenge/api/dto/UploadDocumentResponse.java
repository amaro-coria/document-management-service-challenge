package com.clara.ops.challenge.document_management_service_challenge.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Returned by {@code POST /document-management/upload}. The client must then PUT the PDF bytes
 * directly to {@link #uploadUrl}, and finally call {@code POST
 * /document-management/upload/{id}/complete} to mark the document available.
 */
public record UploadDocumentResponse(UUID id, String uploadUrl, OffsetDateTime expiresAt) {}
