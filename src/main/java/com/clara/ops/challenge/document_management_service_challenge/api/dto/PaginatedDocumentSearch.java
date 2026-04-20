package com.clara.ops.challenge.document_management_service_challenge.api.dto;

import java.util.List;

public record PaginatedDocumentSearch(MetadataDto metadata, List<DocumentDto> documents) {}
