package com.clara.ops.challenge.document_management_service_challenge.api.dto;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

public record DocumentDto(
    UUID id,
    String user,
    String name,
    Set<String> tags,
    Long size,
    String type,
    OffsetDateTime createdAt) {}
