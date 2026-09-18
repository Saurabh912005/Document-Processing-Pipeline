package com.suretyseven.documentprocessing.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suretyseven.documentprocessing.domain.Document;
import com.suretyseven.documentprocessing.domain.DocumentStatus;
import com.suretyseven.documentprocessing.domain.ExtractedResult;
import com.suretyseven.documentprocessing.processor.MockExtractionPayload;
import com.suretyseven.documentprocessing.repository.DocumentRepository;
import com.suretyseven.documentprocessing.repository.ExtractedResultRepository;
import com.suretyseven.documentprocessing.validation.ExtractedDataValidator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentProcessingStateService {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingStateService.class);

    private final DocumentRepository documentRepository;
    private final ExtractedResultRepository extractedResultRepository;
    private final DocumentHistoryService historyService;
    private final ExtractedDataValidator validator;
    private final ObjectMapper objectMapper;

    public DocumentProcessingStateService(
            DocumentRepository documentRepository,
            ExtractedResultRepository extractedResultRepository,
            DocumentHistoryService historyService,
            ExtractedDataValidator validator,
            ObjectMapper objectMapper) {
        this.documentRepository = documentRepository;
        this.extractedResultRepository = extractedResultRepository;
        this.historyService = historyService;
        this.validator = validator;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public boolean markProcessing(String documentId, int attempt) {
        Document document = documentRepository.findById(documentId).orElse(null);
        if (document == null) {
            return false;
        }
        if (document.getStatus() == DocumentStatus.PROCESSED) {
            return false;
        }
        document.setStatus(DocumentStatus.PROCESSING);
        documentRepository.save(document);
        historyService.record(documentId, DocumentStatus.PROCESSING, "Attempt " + attempt, attempt);
        return true;
    }

    /** @return true if processing should stop (validation failed terminally) */
    @Transactional
    public boolean applyValidation(String documentId, MockExtractionPayload payload, int attempt) {
        List<String> errors = validator.validate(payload);
        persistExtracted(documentId, payload, errors);

        if (!errors.isEmpty()) {
            Document document = documentRepository.findById(documentId).orElseThrow();
            document.setStatus(DocumentStatus.FAILED);
            document.setFailureReason("VALIDATION_FAILED");
            documentRepository.save(document);
            historyService.record(
                    documentId,
                    DocumentStatus.FAILED,
                    "VALIDATION_FAILED: " + String.join("; ", errors),
                    attempt);
            log.info(
                    "document_validation documentId={} attempt={} fieldErrorCount={}",
                    documentId,
                    attempt,
                    errors.size());
            return true;
        }
        return false;
    }

    @Transactional
    public void markProcessed(String documentId, MockExtractionPayload payload, int attempt) {
        persistExtracted(documentId, payload, List.of());
        Document document = documentRepository.findById(documentId).orElseThrow();
        document.setStatus(DocumentStatus.PROCESSED);
        document.setFailureReason(null);
        documentRepository.save(document);
        historyService.record(documentId, DocumentStatus.PROCESSED, null, attempt);
    }

    /** @return true if no further retries should run */
    @Transactional
    public boolean markTransientFailure(String documentId, int attempt, String reason, int maxAttempts) {
        Document document = documentRepository.findById(documentId).orElse(null);
        if (document == null) {
            return true;
        }
        document.setRetryCount(attempt);
        if (attempt >= maxAttempts) {
            document.setStatus(DocumentStatus.FAILED);
            document.setFailureReason(reason + "_EXHAUSTED");
            documentRepository.save(document);
            historyService.record(documentId, DocumentStatus.FAILED, reason + "_EXHAUSTED", attempt);
            return true;
        }
        document.setStatus(DocumentStatus.FAILED);
        document.setFailureReason(reason);
        documentRepository.save(document);
        historyService.record(documentId, DocumentStatus.FAILED, reason, attempt);
        return false;
    }

    private void persistExtracted(String documentId, MockExtractionPayload payload, List<String> errors) {
        ExtractedResult result = extractedResultRepository.findById(documentId).orElseGet(ExtractedResult::new);
        Document document = documentRepository.findById(documentId).orElseThrow();
        result.setDocument(document);
        result.setCompanyName(payload.companyName());
        result.setRegistrationNumber(payload.registrationNumber());
        result.setAddress(payload.address());
        result.setAnnualRevenue(payload.annualRevenue());
        result.setDocumentDate(payload.documentDate());
        result.setValidationErrors(toJson(errors));
        extractedResultRepository.save(result);
    }

    private String toJson(List<String> errors) {
        if (errors == null || errors.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(errors);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }
}
