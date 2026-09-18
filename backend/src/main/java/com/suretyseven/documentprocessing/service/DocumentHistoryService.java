package com.suretyseven.documentprocessing.service;

import com.suretyseven.documentprocessing.domain.DocumentHistoryEvent;
import com.suretyseven.documentprocessing.domain.DocumentStatus;
import com.suretyseven.documentprocessing.repository.DocumentHistoryEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentHistoryService {

    private static final Logger log = LoggerFactory.getLogger(DocumentHistoryService.class);

    private final DocumentHistoryEventRepository historyRepository;

    public DocumentHistoryService(DocumentHistoryEventRepository historyRepository) {
        this.historyRepository = historyRepository;
    }

    @Transactional
    public void record(String documentId, DocumentStatus status, String reason, int attempt) {
        DocumentHistoryEvent event = new DocumentHistoryEvent();
        event.setDocumentId(documentId);
        event.setStatus(status);
        event.setReason(reason);
        historyRepository.save(event);
        log.info(
                "document_transition documentId={} status={} attempt={} reason={}",
                documentId,
                status,
                attempt,
                reason == null ? "" : reason);
    }
}
