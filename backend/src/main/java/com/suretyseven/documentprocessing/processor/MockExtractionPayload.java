package com.suretyseven.documentprocessing.processor;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MockExtractionPayload(
        String companyName,
        String registrationNumber,
        String address,
        BigDecimal annualRevenue,
        LocalDate documentDate) {}
