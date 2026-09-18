package com.suretyseven.documentprocessing.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "document_id_sequence")
@Getter
@Setter
public class DocumentIdSequence {

    @Id
    private Long id = 1L;

    private long nextValue = 1L;
}
