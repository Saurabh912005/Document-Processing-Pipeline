package com.suretyseven.documentprocessing.validation;

import com.suretyseven.documentprocessing.processor.MockExtractionPayload;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ExtractedDataValidator {

    public List<String> validate(MockExtractionPayload payload) {
        List<String> errors = new ArrayList<>();
        if (payload.companyName() == null || payload.companyName().isBlank()) {
            errors.add("companyName: required, non-blank");
        }
        if (payload.registrationNumber() == null || payload.registrationNumber().isBlank()) {
            errors.add("registrationNumber: required, non-blank");
        }
        if (payload.annualRevenue() == null) {
            errors.add("annualRevenue: required");
        } else if (payload.annualRevenue().signum() < 0) {
            errors.add("annualRevenue: must be >= 0");
        }
        if (payload.documentDate() == null) {
            errors.add("documentDate: required, valid ISO date");
        }
        return errors;
    }
}
