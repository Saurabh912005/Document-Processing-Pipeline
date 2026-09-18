package com.suretyseven.documentprocessing.web;

import com.suretyseven.documentprocessing.domain.DocumentStatus;
import com.suretyseven.documentprocessing.domain.DocumentType;
import com.suretyseven.documentprocessing.service.DocumentService;
import com.suretyseven.documentprocessing.web.dto.DashboardCountsResponse;
import com.suretyseven.documentprocessing.web.dto.DocumentDetailResponse;
import com.suretyseven.documentprocessing.web.dto.DocumentHistoryResponse;
import com.suretyseven.documentprocessing.web.dto.DocumentListItemResponse;
import com.suretyseven.documentprocessing.web.dto.DocumentUploadResponse;
import com.suretyseven.documentprocessing.web.dto.PageResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentUploadResponse> uploadDocument(
            @RequestPart("file") MultipartFile file,
            @RequestParam("documentType") DocumentType documentType,
            @RequestParam(required = false) Map<String, String> metadata)
            throws IOException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        var result = documentService.upload(file, documentType, documentService.parseMetadata(metadata));
        DocumentUploadResponse body = new DocumentUploadResponse(result.documentId(), result.status(), result.duplicate());
        if (result.duplicate()) {
            return ResponseEntity.ok(body);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping("/{documentId}")
    public DocumentDetailResponse getDocument(@PathVariable String documentId) {
        return documentService.getDocument(documentId);
    }

    @GetMapping("/{documentId}/history")
    public java.util.List<DocumentHistoryResponse> getHistory(@PathVariable String documentId) {
        return documentService.getHistory(documentId);
    }

    @GetMapping
    public PageResponse<DocumentListItemResponse> listDocuments(
            @RequestParam(required = false) DocumentStatus status,
            @RequestParam(required = false) DocumentType documentType,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return documentService.listDocuments(status, documentType, search, page, size);
    }

    @GetMapping("/stats/dashboard")
    public DashboardCountsResponse dashboard() {
        return documentService.dashboardCounts();
    }
}
