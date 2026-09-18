package com.suretyseven.documentprocessing.web.dto;

import com.suretyseven.documentprocessing.domain.DocumentStatus;
import java.time.Instant;

public record DocumentHistoryResponse(DocumentStatus status, String reason, Instant timestamp) {}
