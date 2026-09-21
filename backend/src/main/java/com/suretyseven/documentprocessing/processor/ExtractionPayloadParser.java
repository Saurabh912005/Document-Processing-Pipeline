package com.suretyseven.documentprocessing.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class ExtractionPayloadParser {

    private static final Pattern COMPANY_NAME =
            Pattern.compile("(?i)company\\s*name\\s*[:=]\\s*(.+)", Pattern.MULTILINE);
    private static final Pattern REGISTRATION =
            Pattern.compile("(?i)registration\\s*number\\s*[:=]\\s*(.+)", Pattern.MULTILINE);
    private static final Pattern ADDRESS = Pattern.compile("(?i)address\\s*[:=]\\s*(.+)", Pattern.MULTILINE);
    private static final Pattern REVENUE =
            Pattern.compile("(?i)annual\\s*revenue\\s*[:=]\\s*([0-9,]+(?:\\.[0-9]+)?)", Pattern.MULTILINE);
    private static final Pattern DOCUMENT_DATE =
            Pattern.compile("(?i)document\\s*date\\s*[:=]\\s*(\\d{4}-\\d{2}-\\d{2})", Pattern.MULTILINE);

    private final ObjectMapper objectMapper;

    public ExtractionPayloadParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public MockExtractionPayload parse(String text) {
        if (ReferenceExtractionTemplate.matchesReference(text)) {
            return ReferenceExtractionTemplate.CANONICAL;
        }
        return parseJsonBlock(text).orElseGet(() -> parseLabeledLines(text));
    }

    private Optional<MockExtractionPayload> parseJsonBlock(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return Optional.empty();
        }
        try {
            JsonNode node = objectMapper.readTree(text.substring(start, end + 1));
            return Optional.of(fromJson(node));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private MockExtractionPayload fromJson(JsonNode node) {
        String companyName = textOrNull(node, "companyName");
        String registrationNumber = textOrNull(node, "registrationNumber");
        String address = textOrNull(node, "address");
        BigDecimal annualRevenue = null;
        if (node.hasNonNull("annualRevenue") && !node.get("annualRevenue").isNull()) {
            annualRevenue = node.get("annualRevenue").decimalValue();
        }
        LocalDate documentDate = null;
        if (node.hasNonNull("documentDate")) {
            try {
                documentDate = LocalDate.parse(node.get("documentDate").asText().trim());
            } catch (DateTimeParseException ignored) {
                documentDate = null;
            }
        }
        return new MockExtractionPayload(companyName, registrationNumber, address, annualRevenue, documentDate);
    }

    private MockExtractionPayload parseLabeledLines(String text) {
        return new MockExtractionPayload(
                firstGroup(COMPANY_NAME, text),
                firstGroup(REGISTRATION, text),
                firstGroup(ADDRESS, text),
                parseDecimal(firstGroup(REVENUE, text)),
                parseDate(firstGroup(DOCUMENT_DATE, text)));
    }

    private static String textOrNull(JsonNode node, String field) {
        if (!node.has(field) || node.get(field).isNull()) {
            return null;
        }
        String value = node.get(field).asText().trim();
        return value.isEmpty() ? null : value;
    }

    private static String firstGroup(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        String value = matcher.group(1).trim();
        return value.isEmpty() ? null : value;
    }

    private static BigDecimal parseDecimal(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
