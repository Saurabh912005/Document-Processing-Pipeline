package com.suretyseven.documentprocessing.web.dto;

import com.suretyseven.documentprocessing.domain.DocumentStatus;
import com.suretyseven.documentprocessing.domain.DocumentType;
import java.time.Instant;

public record DocumentListItemResponse(
        String id,
        String filename,
        DocumentType documentType,
        DocumentStatus status,
        Instant createdAt) {}
