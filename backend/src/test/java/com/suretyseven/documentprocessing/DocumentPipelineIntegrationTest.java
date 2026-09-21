package com.suretyseven.documentprocessing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suretyseven.documentprocessing.domain.DocumentStatus;
import com.suretyseven.documentprocessing.processor.DocumentFieldExtractor;
import com.suretyseven.documentprocessing.processor.MockExtractionPayload;
import com.suretyseven.documentprocessing.processor.ReferenceExtractionTemplate;
import com.suretyseven.documentprocessing.repository.DocumentHistoryEventRepository;
import com.suretyseven.documentprocessing.repository.DocumentRepository;
import com.suretyseven.documentprocessing.repository.ExtractedResultRepository;
import com.suretyseven.documentprocessing.service.DocumentProcessingOrchestrator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DocumentPipelineIntegrationTest {

    private static final String REFERENCE_JSON =
            """
            {
              "companyName": "ABC Construction Pvt Ltd",
              "registrationNumber": "U12345DL2020PTC123456",
              "address": "New Delhi",
              "annualRevenue": 12500000,
              "documentDate": "2026-08-15"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentHistoryEventRepository historyRepository;

    @Autowired
    private ExtractedResultRepository extractedResultRepository;

    @Autowired
    private DocumentProcessingOrchestrator orchestrator;

    @MockBean
    private DocumentFieldExtractor fieldExtractor;

    @BeforeEach
    void setUp() throws Exception {
        historyRepository.deleteAll();
        extractedResultRepository.deleteAll();
        documentRepository.deleteAll();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void uploadEnqueuesProcessingAfterCommit() throws Exception {
        when(fieldExtractor.extract(any())).thenReturn(ReferenceExtractionTemplate.CANONICAL);

        String documentId = uploadAndGetId(documentFile("async-after-commit.pdf", REFERENCE_JSON));

        DocumentStatus terminal = DocumentStatus.UPLOADED;
        for (int i = 0; i < 50 && terminal == DocumentStatus.UPLOADED; i++) {
            Thread.sleep(100);
            terminal = documentRepository.findById(documentId).orElseThrow().getStatus();
        }
        assertThat(terminal).isNotEqualTo(DocumentStatus.UPLOADED);
    }

    @Test
    void uploadValidDocumentReturns201WithUploadedStatus() throws Exception {
        MockMultipartFile file = documentFile("statement.pdf", REFERENCE_JSON);
        mockMvc.perform(multipart("/api/documents")
                        .file(file)
                        .param("documentType", "FINANCIAL_STATEMENT"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("UPLOADED"))
                .andExpect(jsonPath("$.duplicate").value(false))
                .andExpect(jsonPath("$.documentId").value(org.hamcrest.Matchers.startsWith("DOC-")));
    }

    @Test
    void duplicateUploadReturnsExistingDocumentWith200() throws Exception {
        MockMultipartFile file = documentFile("dup.pdf", REFERENCE_JSON);
        MvcResult first = mockMvc.perform(multipart("/api/documents").file(file).param("documentType", "OTHER"))
                .andExpect(status().isCreated())
                .andReturn();
        String firstId = objectMapper.readTree(first.getResponse().getContentAsString()).get("documentId").asText();

        mockMvc.perform(multipart("/api/documents").file(file).param("documentType", "OTHER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true))
                .andExpect(jsonPath("$.documentId").value(firstId));

        assertThat(documentRepository.findByFileHash(
                        documentRepository.findById(firstId).orElseThrow().getFileHash()))
                .isPresent();
        assertThat(documentRepository.findAll().stream()
                        .map(com.suretyseven.documentprocessing.domain.Document::getFileHash)
                        .distinct()
                        .count())
                .isEqualTo(1);
    }

    @Test
    void invalidExtractedDataMarksValidationFailure() throws Exception {
        when(fieldExtractor.extract(any()))
                .thenReturn(new MockExtractionPayload("", "REG", "Addr", null, null));

        String documentId = uploadAndGetId(documentFile("invalid.pdf", "no fields"));
        orchestrator.processWithRetries(documentId);

        mockMvc.perform(get("/api/documents/{id}", documentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.extractedResult.validationErrors").isArray())
                .andExpect(jsonPath("$.extractedResult.validationErrors[0]").exists());
    }

    @Test
    void successfulProcessingEndToEnd() throws Exception {
        when(fieldExtractor.extract(any())).thenReturn(ReferenceExtractionTemplate.CANONICAL);

        String documentId = uploadAndGetId(documentFile("success.pdf", REFERENCE_JSON));
        orchestrator.processWithRetries(documentId);

        mockMvc.perform(get("/api/documents/{id}", documentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSED"))
                .andExpect(jsonPath("$.extractedResult.companyName").value("ABC Construction Pvt Ltd"));
    }

    @Test
    void processorReadErrorMarksFailedWithReasonInHistory() throws Exception {
        when(fieldExtractor.extract(any())).thenThrow(new IOException("simulated read failure"));

        String documentId = uploadAndGetId(documentFile("read-error.pdf", REFERENCE_JSON));
        orchestrator.processWithRetries(documentId);

        mockMvc.perform(get("/api/documents/{id}", documentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));

        mockMvc.perform(get("/api/documents/{id}/history", documentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.reason == 'PROCESSOR_ERROR_EXHAUSTED')]").exists());
    }

    @Test
    void failureThenSuccessOnRetryShowsHistoryAndProcessedStatus() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        when(fieldExtractor.extract(any())).thenAnswer(inv -> {
            if (calls.getAndIncrement() == 0) {
                throw new IOException("transient");
            }
            return ReferenceExtractionTemplate.CANONICAL;
        });

        String documentId = uploadAndGetId(documentFile("retry.pdf", REFERENCE_JSON));
        orchestrator.processWithRetries(documentId);

        mockMvc.perform(get("/api/documents/{id}", documentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSED"));

        var history = historyRepository.findByDocumentIdOrderByTimestampAsc(documentId);
        assertThat(history.stream().anyMatch(h -> "PROCESSOR_ERROR".equals(h.getReason()))).isTrue();
        assertThat(history.stream().anyMatch(h -> h.getStatus() == DocumentStatus.PROCESSED)).isTrue();
    }

    private String uploadAndGetId(MockMultipartFile file) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/documents")
                        .file(file)
                        .param("documentType", "FINANCIAL_STATEMENT"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("documentId").asText();
    }

    private MockMultipartFile documentFile(String name, String content) {
        return new MockMultipartFile(
                "file", name, MediaType.APPLICATION_PDF_VALUE, content.getBytes(StandardCharsets.UTF_8));
    }
}
