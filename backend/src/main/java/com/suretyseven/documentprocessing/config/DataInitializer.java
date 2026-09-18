package com.suretyseven.documentprocessing.config;

import com.suretyseven.documentprocessing.domain.DocumentIdSequence;
import com.suretyseven.documentprocessing.repository.DocumentIdSequenceRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements ApplicationRunner {

    private final DocumentIdSequenceRepository sequenceRepository;

    public DataInitializer(DocumentIdSequenceRepository sequenceRepository) {
        this.sequenceRepository = sequenceRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (sequenceRepository.findById(1L).isEmpty()) {
            DocumentIdSequence sequence = new DocumentIdSequence();
            sequence.setId(1L);
            sequence.setNextValue(1L);
            sequenceRepository.save(sequence);
        }
    }
}
