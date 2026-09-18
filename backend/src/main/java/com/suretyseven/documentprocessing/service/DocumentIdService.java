package com.suretyseven.documentprocessing.service;

import com.suretyseven.documentprocessing.domain.DocumentIdSequence;
import com.suretyseven.documentprocessing.repository.DocumentIdSequenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentIdService {

    private final DocumentIdSequenceRepository sequenceRepository;

    public DocumentIdService(DocumentIdSequenceRepository sequenceRepository) {
        this.sequenceRepository = sequenceRepository;
    }

    @Transactional
    public String nextDocumentId() {
        DocumentIdSequence sequence = sequenceRepository.lockSequenceRow()
                .orElseGet(this::initializeSequence);
        long value = sequence.getNextValue();
        sequence.setNextValue(value + 1);
        sequenceRepository.save(sequence);
        return String.format("DOC-%05d", value);
    }

    private DocumentIdSequence initializeSequence() {
        DocumentIdSequence sequence = new DocumentIdSequence();
        sequence.setId(1L);
        sequence.setNextValue(1L);
        return sequenceRepository.save(sequence);
    }
}
