package com.suretyseven.documentprocessing.repository;

import com.suretyseven.documentprocessing.domain.Document;
import com.suretyseven.documentprocessing.domain.DocumentStatus;
import com.suretyseven.documentprocessing.domain.DocumentType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentRepository extends JpaRepository<Document, String> {

    Optional<Document> findByFileHash(String fileHash);

    @Query("""
            SELECT d FROM Document d
            WHERE (:status IS NULL OR d.status = :status)
              AND (:documentType IS NULL OR d.documentType = :documentType)
              AND (:search IS NULL OR LOWER(d.filename) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<Document> findFiltered(
            @Param("status") DocumentStatus status,
            @Param("documentType") DocumentType documentType,
            @Param("search") String search,
            Pageable pageable);

    long countByStatus(DocumentStatus status);

    List<Document> findByStatusAndUpdatedAtBefore(DocumentStatus status, Instant updatedAtBefore);
}
