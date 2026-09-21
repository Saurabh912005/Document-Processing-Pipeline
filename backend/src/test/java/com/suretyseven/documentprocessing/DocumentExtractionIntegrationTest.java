package com.suretyseven.documentprocessing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suretyseven.documentprocessing.domain.DocumentStatus;
import com.suretyseven.documentprocessing.repository.DocumentRepository;
import com.suretyseven.documentprocessing.service.DocumentProcessingOrchestrator;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DocumentExtractionIntegrationTest {

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
    private DocumentProcessingOrchestrator orchestrator;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void extractsFieldsFromUploadedDocumentContent() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "reference.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                REFERENCE_JSON.getBytes(StandardCharsets.UTF_8));

        var upload = mockMvc.perform(multipart("/api/documents")
                        .file(file)
                        .param("documentType", "FINANCIAL_STATEMENT"))
                .andExpect(status().isCreated())
                .andReturn();

        String documentId =
                objectMapper.readTree(upload.getResponse().getContentAsString()).get("documentId").asText();

        orchestrator.processWithRetries(documentId);

        var document = documentRepository.findById(documentId).orElseThrow();
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.PROCESSED);
    }
}
