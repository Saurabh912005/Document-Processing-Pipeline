package com.suretyseven.documentprocessing.service;

public class DocumentNotFoundException extends RuntimeException {

    public DocumentNotFoundException(String documentId) {
        super("Document not found: " + documentId);
    }
}
