package com.suretyseven.documentprocessing.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class DocumentProcessingTrigger {

    private final DocumentProcessingOrchestrator processingOrchestrator;

    public DocumentProcessingTrigger(DocumentProcessingOrchestrator processingOrchestrator) {
        this.processingOrchestrator = processingOrchestrator;
    }

    /** Enqueue async processing only after the current transaction commits (visible to other threads). */
    public void enqueueAfterCommit(String documentId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            processingOrchestrator.processDocumentAsync(documentId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                processingOrchestrator.processDocumentAsync(documentId);
            }
        });
    }
}
