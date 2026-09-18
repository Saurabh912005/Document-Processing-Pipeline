package com.suretyseven.documentprocessing.service;

import com.suretyseven.documentprocessing.config.ProcessingProperties;
import com.suretyseven.documentprocessing.domain.Document;
import com.suretyseven.documentprocessing.domain.DocumentStatus;
import com.suretyseven.documentprocessing.domain.ProcessorOutcome;
import com.suretyseven.documentprocessing.processor.MockDocumentProcessor;
import com.suretyseven.documentprocessing.processor.MockExtractionPayload;
import com.suretyseven.documentprocessing.repository.DocumentRepository;
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
    private final MockDocumentProcessor mockProcessor;
    private final ProcessingProperties processingProperties;

    public DocumentProcessingOrchestrator(
            DocumentRepository documentRepository,
            DocumentProcessingStateService stateService,
            MockDocumentProcessor mockProcessor,
            ProcessingProperties processingProperties) {
        this.documentRepository = documentRepository;
        this.stateService = stateService;
        this.mockProcessor = mockProcessor;
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

            ProcessorOutcome outcome = mockProcessor.rollOutcomeRandom();
            try {
                mockProcessor.simulateDelay();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                if (handleTransientFailure(documentId, attempt, "PROCESSOR_INTERRUPTED", maxAttempts, backoff)) {
                    return;
                }
                document = documentRepository.findById(documentId).orElse(null);
                if (document == null || document.getStatus() == DocumentStatus.FAILED) {
                    return;
                }
                continue;
            }

            log.info(
                    "document_processing documentId={} attempt={} outcome={}",
                    documentId,
                    attempt,
                    outcome);

            switch (outcome) {
                case SUCCESS -> {
                    MockExtractionPayload payload = mockProcessor.generateSuccessPayload(document.getFileHash());
                    if (stateService.applyValidation(documentId, payload, attempt)) {
                        return;
                    }
                    stateService.markProcessed(documentId, payload, attempt);
                    return;
                }
                case INVALID_RESULT -> {
                    MockExtractionPayload payload = mockProcessor.generateInvalidPayload(document.getFileHash());
                    stateService.applyValidation(documentId, payload, attempt);
                    return;
                }
                case TIMEOUT -> {
                    if (handleTransientFailure(documentId, attempt, "PROCESSOR_TIMEOUT", maxAttempts, backoff)) {
                        return;
                    }
                }
                case ERROR -> {
                    if (handleTransientFailure(documentId, attempt, "PROCESSOR_ERROR", maxAttempts, backoff)) {
                        return;
                    }
                }
                default -> {
                    // no-op
                }
            }
            document = documentRepository.findById(documentId).orElse(null);
            if (document == null) {
                return;
            }
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
