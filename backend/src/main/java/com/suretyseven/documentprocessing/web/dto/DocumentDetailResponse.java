package com.suretyseven.documentprocessing.web.dto;

import com.suretyseven.documentprocessing.domain.DocumentStatus;
import com.suretyseven.documentprocessing.domain.DocumentType;
import java.time.Instant;
import java.util.Map;

public record DocumentDetailResponse(
        String id,
        String filename,
        DocumentType documentType,
        DocumentStatus status,
        Map<String, Object> metadata,
        String fileHash,
        Instant createdAt,
        Instant updatedAt,
        int retryCount,
        String failureReason,
        ExtractedResultResponse extractedResult) {}
