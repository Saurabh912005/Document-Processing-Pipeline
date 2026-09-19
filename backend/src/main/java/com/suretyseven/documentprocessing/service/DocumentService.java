package com.suretyseven.documentprocessing.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suretyseven.documentprocessing.domain.Document;
import com.suretyseven.documentprocessing.domain.DocumentHistoryEvent;
import com.suretyseven.documentprocessing.domain.DocumentStatus;
import com.suretyseven.documentprocessing.domain.DocumentType;
import com.suretyseven.documentprocessing.domain.ExtractedResult;
import com.suretyseven.documentprocessing.repository.DocumentHistoryEventRepository;
import com.suretyseven.documentprocessing.repository.DocumentRepository;
import com.suretyseven.documentprocessing.repository.ExtractedResultRepository;
import com.suretyseven.documentprocessing.web.dto.DashboardCountsResponse;
import com.suretyseven.documentprocessing.web.dto.DocumentDetailResponse;
import com.suretyseven.documentprocessing.web.dto.DocumentHistoryResponse;
import com.suretyseven.documentprocessing.web.dto.DocumentListItemResponse;
import com.suretyseven.documentprocessing.web.dto.DocumentUploadResponse;
import com.suretyseven.documentprocessing.web.dto.ExtractedResultResponse;
import com.suretyseven.documentprocessing.web.dto.PageResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final ExtractedResultRepository extractedResultRepository;
    private final DocumentHistoryEventRepository historyEventRepository;
    private final FileHashService fileHashService;
    private final FileStorageService fileStorageService;
    private final DocumentIdService documentIdService;
    private final DocumentHistoryService historyService;
    private final DocumentProcessingTrigger processingTrigger;
    private final ObjectMapper objectMapper;

    public DocumentService(
            DocumentRepository documentRepository,
            ExtractedResultRepository extractedResultRepository,
            DocumentHistoryEventRepository historyEventRepository,
            FileHashService fileHashService,
            FileStorageService fileStorageService,
            DocumentIdService documentIdService,
            DocumentHistoryService historyService,
            DocumentProcessingTrigger processingTrigger,
            ObjectMapper objectMapper) {
        this.documentRepository = documentRepository;
        this.extractedResultRepository = extractedResultRepository;
        this.historyEventRepository = historyEventRepository;
        this.fileHashService = fileHashService;
        this.fileStorageService = fileStorageService;
        this.documentIdService = documentIdService;
        this.historyService = historyService;
        this.processingTrigger = processingTrigger;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public UploadResult upload(MultipartFile file, DocumentType documentType, String metadataJson) throws IOException {
        String hash = fileHashService.sha256(file);
        Optional<Document> existing = documentRepository.findByFileHash(hash);
        if (existing.isPresent()) {
            return new UploadResult(existing.get().getId(), existing.get().getStatus(), true);
        }

        String documentId = documentIdService.nextDocumentId();
        String storagePath = fileStorageService.store(documentId, file);

        Document document = new Document();
        document.setId(documentId);
        document.setFilename(file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.bin");
        document.setDocumentType(documentType);
        document.setStatus(DocumentStatus.UPLOADED);
        document.setMetadata(metadataJson);
        document.setFileHash(hash);
        document.setStoragePath(storagePath);
        document.setRetryCount(0);

        try {
            documentRepository.save(document);
        } catch (DataIntegrityViolationException ex) {
            Optional<Document> raced = documentRepository.findByFileHash(hash);
            if (raced.isPresent()) {
                return new UploadResult(raced.get().getId(), raced.get().getStatus(), true);
            }
            throw ex;
        }

        historyService.record(documentId, DocumentStatus.UPLOADED, null, 0);
        processingTrigger.enqueueAfterCommit(documentId);
        return new UploadResult(documentId, DocumentStatus.UPLOADED, false);
    }

    public DocumentDetailResponse getDocument(String documentId) {
        Document document = documentRepository
                .findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));
        ExtractedResult result = extractedResultRepository.findById(documentId).orElse(null);
        return toDetail(document, result);
    }

    public List<DocumentHistoryResponse> getHistory(String documentId) {
        if (!documentRepository.existsById(documentId)) {
            throw new DocumentNotFoundException(documentId);
        }
        return historyEventRepository.findByDocumentIdOrderByTimestampAsc(documentId).stream()
                .map(this::toHistory)
                .toList();
    }

    public PageResponse<DocumentListItemResponse> listDocuments(
            DocumentStatus status, DocumentType documentType, String search, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Document> result = documentRepository.findFiltered(status, documentType, blankToNull(search), pageable);
        List<DocumentListItemResponse> items =
                result.getContent().stream().map(this::toListItem).toList();
        return new PageResponse<>(items, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    public DashboardCountsResponse dashboardCounts() {
        long total = documentRepository.count();
        return new DashboardCountsResponse(
                total,
                documentRepository.countByStatus(DocumentStatus.PROCESSING),
                documentRepository.countByStatus(DocumentStatus.PROCESSED),
                documentRepository.countByStatus(DocumentStatus.FAILED));
    }

    public String parseMetadata(Map<String, String> metadata) throws JsonProcessingException {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        return objectMapper.writeValueAsString(metadata);
    }

    private DocumentListItemResponse toListItem(Document document) {
        return new DocumentListItemResponse(
                document.getId(),
                document.getFilename(),
                document.getDocumentType(),
                document.getStatus(),
                document.getCreatedAt());
    }

    private DocumentDetailResponse toDetail(Document document, ExtractedResult result) {
        Map<String, Object> metadata = parseMetadataMap(document.getMetadata());
        ExtractedResultResponse extracted = null;
        if (result != null) {
            extracted = new ExtractedResultResponse(
                    result.getCompanyName(),
                    result.getRegistrationNumber(),
                    result.getAddress(),
                    result.getAnnualRevenue(),
                    result.getDocumentDate(),
                    parseValidationErrors(result.getValidationErrors()));
        }
        String safeFailure = document.getStatus() == DocumentStatus.FAILED ? sanitizeFailure(document.getFailureReason()) : null;
        return new DocumentDetailResponse(
                document.getId(),
                document.getFilename(),
                document.getDocumentType(),
                document.getStatus(),
                metadata,
                document.getFileHash(),
                document.getCreatedAt(),
                document.getUpdatedAt(),
                document.getRetryCount(),
                safeFailure,
                extracted);
    }

    private DocumentHistoryResponse toHistory(DocumentHistoryEvent event) {
        return new DocumentHistoryResponse(event.getStatus(), event.getReason(), event.getTimestamp());
    }

    private Map<String, Object> parseMetadataMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return Map.of("raw", json);
        }
    }

    private List<String> parseValidationErrors(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return List.of(json);
        }
    }

    private String sanitizeFailure(String reason) {
        if (reason == null) {
            return "Processing failed";
        }
        if (reason.contains("VALIDATION_FAILED")) {
            return "Validation failed — see validation errors below";
        }
        if (reason.contains("TIMEOUT")) {
            return "Processing timed out";
        }
        if (reason.contains("ERROR")) {
            return "Processing error";
        }
        return reason;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record UploadResult(String documentId, DocumentStatus status, boolean duplicate) {}
}
