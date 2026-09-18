package com.suretyseven.documentprocessing.web.dto;

import com.suretyseven.documentprocessing.domain.DocumentStatus;

public record DocumentUploadResponse(String documentId, DocumentStatus status, boolean duplicate) {}
