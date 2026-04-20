package com.clara.ops.challenge.document_management_service_challenge.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record UploadDocumentRequest(
    @NotBlank @Size(max = 255) String user,
    @NotBlank @Size(max = 512) String name,
    @NotEmpty Set<@NotBlank @Size(max = 128) String> tags) {}
