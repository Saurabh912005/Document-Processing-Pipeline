package com.suretyseven.documentprocessing.repository;

import com.suretyseven.documentprocessing.domain.ExtractedResult;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExtractedResultRepository extends JpaRepository<ExtractedResult, String> {}
