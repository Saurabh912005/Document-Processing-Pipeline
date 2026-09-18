package com.suretyseven.documentprocessing.service;

import com.suretyseven.documentprocessing.domain.Document;
import com.suretyseven.documentprocessing.domain.DocumentStatus;
import com.suretyseven.documentprocessing.repository.DocumentRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class StuckDocumentRecoveryJob {

    private static final Logger log = LoggerFactory.getLogger(StuckDocumentRecoveryJob.class);

    private final DocumentRepository documentRepository;
    private final DocumentProcessingOrchestrator processingOrchestrator;

    public StuckDocumentRecoveryJob(
            DocumentRepository documentRepository, DocumentProcessingOrchestrator processingOrchestrator) {
        this.documentRepository = documentRepository;
        this.processingOrchestrator = processingOrchestrator;
    }

    @Scheduled(fixedDelayString = "${document.recovery.fixed-delay-ms:60000}")
    @Transactional
    public void recoverStuckDocuments() {
        Instant cutoff = Instant.now().minusSeconds(120);
        List<Document> stuck =
                documentRepository.findByStatusAndUpdatedAtBefore(DocumentStatus.PROCESSING, cutoff);
        for (Document document : stuck) {
            log.warn(
                    "document_recovery documentId={} status=PROCESSING reason=STALE_PROCESSING_REQUEUED",
                    document.getId());
            document.setStatus(DocumentStatus.UPLOADED);
            documentRepository.save(document);
            processingOrchestrator.processDocumentAsync(document.getId());
        }
    }
}
