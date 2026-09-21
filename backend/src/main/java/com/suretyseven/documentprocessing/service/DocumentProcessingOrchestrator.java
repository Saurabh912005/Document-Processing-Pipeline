package com.suretyseven.documentprocessing.service;

import com.suretyseven.documentprocessing.config.ProcessingProperties;
import com.suretyseven.documentprocessing.domain.Document;
import com.suretyseven.documentprocessing.domain.DocumentStatus;
import com.suretyseven.documentprocessing.processor.DocumentFieldExtractor;
import com.suretyseven.documentprocessing.processor.MockExtractionPayload;
import com.suretyseven.documentprocessing.repository.DocumentRepository;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class DocumentProcessingOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingOrchestrator.class);

    private final DocumentRepository documentRepository;
    private final DocumentProcessingStateService stateService;
    private final DocumentFieldExtractor fieldExtractor;
    private final ProcessingProperties processingProperties;

    public DocumentProcessingOrchestrator(
            DocumentRepository documentRepository,
            DocumentProcessingStateService stateService,
            DocumentFieldExtractor fieldExtractor,
            ProcessingProperties processingProperties) {
        this.documentRepository = documentRepository;
        this.stateService = stateService;
        this.fieldExtractor = fieldExtractor;
        this.processingProperties = processingProperties;
    }

    @Async("documentProcessingExecutor")
    public void processDocumentAsync(String documentId) {
        processWithRetries(documentId);
    }

    public void processWithRetries(String documentId) {
        Document document = documentRepository.findById(documentId).orElse(null);
        if (document == null) {
            log.warn("document_transition documentId={} status=SKIPPED attempt=0 reason=DOCUMENT_NOT_FOUND", documentId);
            return;
        }

        int maxAttempts = processingProperties.maxAttempts();
        List<Integer> backoff = processingProperties.backoffSeconds();

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (!stateService.markProcessing(documentId, attempt)) {
                return;
            }

            document = documentRepository.findById(documentId).orElse(null);
            if (document == null) {
                return;
            }

            MockExtractionPayload payload;
            try {
                payload = fieldExtractor.extract(document);
            } catch (IOException e) {
                log.warn("document_extraction documentId={} attempt={} error={}", documentId, attempt, e.getMessage());
                if (handleTransientFailure(documentId, attempt, "PROCESSOR_ERROR", maxAttempts, backoff)) {
                    return;
                }
                continue;
            }

            log.info("document_processing documentId={} attempt={} outcome=EXTRACTED", documentId, attempt);

            if (stateService.applyValidation(documentId, payload, attempt)) {
                return;
            }
            stateService.markProcessed(documentId, payload, attempt);
            return;
        }
    }

    private boolean handleTransientFailure(
            String documentId, int attempt, String reason, int maxAttempts, List<Integer> backoff) {
        boolean terminal = stateService.markTransientFailure(documentId, attempt, reason, maxAttempts);
        if (terminal) {
            return true;
        }
        int delayIndex = Math.min(attempt - 1, backoff.size() - 1);
        int delaySeconds = backoff.get(delayIndex);
        try {
            Thread.sleep(delaySeconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return false;
    }
}
