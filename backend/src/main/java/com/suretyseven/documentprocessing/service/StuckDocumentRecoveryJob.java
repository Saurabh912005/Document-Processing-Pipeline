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
    private final DocumentProcessingTrigger processingTrigger;

    public StuckDocumentRecoveryJob(
            DocumentRepository documentRepository, DocumentProcessingTrigger processingTrigger) {
        this.documentRepository = documentRepository;
        this.processingTrigger = processingTrigger;
    }

    @Scheduled(fixedDelayString = "${document.recovery.fixed-delay-ms:60000}")
    @Transactional
    public void recoverStuckDocuments() {
        Instant cutoff = Instant.now().minusSeconds(120);
        List<Document> stuckProcessing =
                documentRepository.findByStatusAndUpdatedAtBefore(DocumentStatus.PROCESSING, cutoff);
        for (Document document : stuckProcessing) {
            log.warn(
                    "document_recovery documentId={} status=PROCESSING reason=STALE_PROCESSING_REQUEUED",
                    document.getId());
            document.setStatus(DocumentStatus.UPLOADED);
            documentRepository.save(document);
            processingTrigger.enqueueAfterCommit(document.getId());
        }

        List<Document> stuckUploaded =
                documentRepository.findByStatusAndUpdatedAtBefore(DocumentStatus.UPLOADED, cutoff);
        for (Document document : stuckUploaded) {
            log.warn(
                    "document_recovery documentId={} status=UPLOADED reason=NEVER_PROCESSED_REQUEUED",
                    document.getId());
            processingTrigger.enqueueAfterCommit(document.getId());
        }
    }
}
