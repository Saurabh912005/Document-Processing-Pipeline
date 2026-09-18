package com.suretyseven.documentprocessing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suretyseven.documentprocessing.domain.DocumentStatus;
import com.suretyseven.documentprocessing.domain.ProcessorOutcome;
import com.suretyseven.documentprocessing.processor.MockDocumentProcessor;
import com.suretyseven.documentprocessing.processor.MockExtractionPayload;
import com.suretyseven.documentprocessing.repository.DocumentHistoryEventRepository;
import com.suretyseven.documentprocessing.repository.DocumentRepository;
import com.suretyseven.documentprocessing.repository.ExtractedResultRepository;
import com.suretyseven.documentprocessing.service.DocumentProcessingOrchestrator;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DocumentPipelineIntegrationTest {

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
    private MockDocumentProcessor mockDocumentProcessor;

    @BeforeEach
    void setUp() throws Exception {
        historyRepository.deleteAll();
        extractedResultRepository.deleteAll();
        documentRepository.deleteAll();
        doAnswer(invocation -> null).when(mockDocumentProcessor).simulateDelay();
        when(mockDocumentProcessor.generateSuccessPayload(any()))
                .thenAnswer(inv -> new MockExtractionPayload(
                        "ABC Construction Pvt Ltd",
                        "U12345DL2020PTC123456",
                        "New Delhi",
                        BigDecimal.valueOf(12_500_000),
                        LocalDate.parse("2026-08-15")));
    }

    @Test
    void uploadValidDocumentReturns201WithUploadedStatus() throws Exception {
        MockMultipartFile file = pdf("statement.pdf", "unique-content-1");
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
        MockMultipartFile file = pdf("dup.pdf", "duplicate-bytes");
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
        when(mockDocumentProcessor.rollOutcomeRandom()).thenReturn(ProcessorOutcome.INVALID_RESULT);
        when(mockDocumentProcessor.generateInvalidPayload(any()))
                .thenReturn(new MockExtractionPayload("", "REG", "Addr", BigDecimal.TEN, LocalDate.now()));

        String documentId = uploadAndGetId(pdf("invalid.pdf", "invalid-content"));
        orchestrator.processWithRetries(documentId);

        mockMvc.perform(get("/api/documents/{id}", documentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.extractedResult.validationErrors").isArray())
                .andExpect(jsonPath("$.extractedResult.validationErrors[0]").exists());
    }

    @Test
    void successfulProcessingEndToEnd() throws Exception {
        when(mockDocumentProcessor.rollOutcomeRandom()).thenReturn(ProcessorOutcome.SUCCESS);
        String documentId = uploadAndGetId(pdf("success.pdf", "success-content"));
        orchestrator.processWithRetries(documentId);

        mockMvc.perform(get("/api/documents/{id}", documentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSED"))
                .andExpect(jsonPath("$.extractedResult.companyName").value("ABC Construction Pvt Ltd"));
    }

    @Test
    void processorTimeoutMarksFailedWithReasonInHistory() throws Exception {
        when(mockDocumentProcessor.rollOutcomeRandom()).thenReturn(ProcessorOutcome.TIMEOUT);
        String documentId = uploadAndGetId(pdf("timeout.pdf", "timeout-content"));
        orchestrator.processWithRetries(documentId);

        mockMvc.perform(get("/api/documents/{id}", documentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));

        mockMvc.perform(get("/api/documents/{id}/history", documentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.reason == 'PROCESSOR_TIMEOUT_EXHAUSTED')]").exists());
    }

    @Test
    void failureThenSuccessOnRetryShowsHistoryAndProcessedStatus() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        when(mockDocumentProcessor.rollOutcomeRandom()).thenAnswer(inv -> {
            return calls.getAndIncrement() == 0 ? ProcessorOutcome.TIMEOUT : ProcessorOutcome.SUCCESS;
        });

        String documentId = uploadAndGetId(pdf("retry.pdf", "retry-content"));
        orchestrator.processWithRetries(documentId);

        mockMvc.perform(get("/api/documents/{id}", documentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSED"));

        var history = historyRepository.findByDocumentIdOrderByTimestampAsc(documentId);
        assertThat(history.stream().anyMatch(h -> "PROCESSOR_TIMEOUT".equals(h.getReason()))).isTrue();
        assertThat(history.stream().anyMatch(h -> h.getStatus() == DocumentStatus.PROCESSED)).isTrue();
    }

    private String uploadAndGetId(MockMultipartFile file) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/documents")
                        .file(file)
                        .param("documentType", "FINANCIAL_STATEMENT"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("documentId").asText();
    }

    private MockMultipartFile pdf(String name, String content) {
        return new MockMultipartFile(
                "file", name, MediaType.APPLICATION_PDF_VALUE, content.getBytes(StandardCharsets.UTF_8));
    }
}
