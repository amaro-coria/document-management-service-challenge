package com.clara.ops.challenge.document_management_service_challenge.api.dto;

public record MetadataDto(
    int currentPage, int itemsPerPage, int currentItems, int totalPages, long totalItems) {}
