package com.suretyseven.documentprocessing.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ExtractedResultResponse(
        String companyName,
        String registrationNumber,
        String address,
        BigDecimal annualRevenue,
        LocalDate documentDate,
        List<String> validationErrors) {}
