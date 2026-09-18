package com.suretyseven.documentprocessing.repository;

import com.suretyseven.documentprocessing.domain.DocumentIdSequence;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface DocumentIdSequenceRepository extends JpaRepository<DocumentIdSequence, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM DocumentIdSequence s WHERE s.id = 1")
    Optional<DocumentIdSequence> lockSequenceRow();
}
