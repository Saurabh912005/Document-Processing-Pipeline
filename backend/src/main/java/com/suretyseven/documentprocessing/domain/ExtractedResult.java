package com.suretyseven.documentprocessing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "extracted_results")
@Getter
@Setter
public class ExtractedResult {

    @Id
    @Column(name = "document_id", length = 32)
    private String documentId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "document_id")
    private Document document;

    @Column(name = "company_name")
    private String companyName;

    @Column(name = "registration_number")
    private String registrationNumber;

    private String address;

    @Column(name = "annual_revenue")
    private BigDecimal annualRevenue;

    @Column(name = "document_date")
    private LocalDate documentDate;

    @Column(name = "validation_errors", length = 4000)
    private String validationErrors;
}
