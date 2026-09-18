package com.suretyseven.documentprocessing.processor;

import com.suretyseven.documentprocessing.domain.ProcessorOutcome;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

@Component
public class MockDocumentProcessor {

    public ProcessorOutcome rollOutcome(String fileHash) {
        int bucket = Math.floorMod(fileHash.hashCode(), 100);
        if (bucket < 60) {
            return ProcessorOutcome.SUCCESS;
        }
        if (bucket < 75) {
            return ProcessorOutcome.TIMEOUT;
        }
        if (bucket < 90) {
            return ProcessorOutcome.ERROR;
        }
        return ProcessorOutcome.INVALID_RESULT;
    }

    public ProcessorOutcome rollOutcomeRandom() {
        int roll = ThreadLocalRandom.current().nextInt(100);
        if (roll < 60) {
            return ProcessorOutcome.SUCCESS;
        }
        if (roll < 75) {
            return ProcessorOutcome.TIMEOUT;
        }
        if (roll < 90) {
            return ProcessorOutcome.ERROR;
        }
        return ProcessorOutcome.INVALID_RESULT;
    }

    public void simulateDelay() throws InterruptedException {
        int seconds = ThreadLocalRandom.current().nextInt(1, 6);
        Thread.sleep(seconds * 1000L);
    }

    public MockExtractionPayload generateSuccessPayload(String fileHash) {
        int seed = fileHash.hashCode();
        String companyName = "Company " + Math.abs(seed % 10000);
        String registrationNumber = "U" + Math.abs(seed) + "DL2020PTC" + Math.abs(seed % 100000);
        String address = switch (Math.abs(seed) % 3) {
            case 0 -> "New Delhi";
            case 1 -> "Mumbai";
            default -> "Bengaluru";
        };
        BigDecimal revenue = BigDecimal.valueOf(1_000_000L + Math.abs(seed % 50_000_000L));
        LocalDate documentDate = LocalDate.of(2020 + Math.abs(seed % 6), 1 + Math.abs(seed % 12), 1 + Math.abs(seed % 28));
        return new MockExtractionPayload(companyName, registrationNumber, address, revenue, documentDate);
    }

    public MockExtractionPayload generateInvalidPayload(String fileHash) {
        int variant = Math.floorMod(fileHash.hashCode(), 3);
        return switch (variant) {
            case 0 -> new MockExtractionPayload("", "REG123", "Address", BigDecimal.TEN, LocalDate.now());
            case 1 -> new MockExtractionPayload("Acme Corp", "", "Address", BigDecimal.TEN, LocalDate.now());
            default -> new MockExtractionPayload("Acme Corp", "REG123", "Address", BigDecimal.valueOf(-1), LocalDate.now());
        };
    }

    public byte[] fakeProcessBytes(String fileHash) {
        return ("processed-" + fileHash).getBytes(StandardCharsets.UTF_8);
    }
}
