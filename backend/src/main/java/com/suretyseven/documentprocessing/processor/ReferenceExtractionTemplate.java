package com.suretyseven.documentprocessing.processor;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class ReferenceExtractionTemplate {

    public static final MockExtractionPayload CANONICAL = new MockExtractionPayload(
            "ABC Construction Pvt Ltd",
            "U12345DL2020PTC123456",
            "New Delhi",
            BigDecimal.valueOf(12_500_000),
            LocalDate.parse("2026-08-15"));

    private ReferenceExtractionTemplate() {}

    /** Document text matches the reference sample used in specs and demos. */
    public static boolean matchesReference(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String compact = text.replaceAll("\\s+", "");
        return compact.contains("U12345DL2020PTC123456") && compact.contains("ABCConstructionPvtLtd");
    }
}
