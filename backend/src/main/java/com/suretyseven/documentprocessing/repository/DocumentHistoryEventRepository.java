package com.suretyseven.documentprocessing.repository;

import com.suretyseven.documentprocessing.domain.DocumentHistoryEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentHistoryEventRepository extends JpaRepository<DocumentHistoryEvent, java.util.UUID> {

    List<DocumentHistoryEvent> findByDocumentIdOrderByTimestampAsc(String documentId);
}
