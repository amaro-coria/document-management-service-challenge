package com.clara.ops.challenge.document_management_service_challenge.api.dto;

import java.util.Set;

/** All fields are optional. An empty/null request returns every AVAILABLE document. */
public record DocumentSearchFilters(String user, String name, Set<String> tags) {}
