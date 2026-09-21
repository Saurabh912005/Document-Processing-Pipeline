package com.suretyseven.documentprocessing.processor;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ExtractionPayloadParserTest {

    private final ExtractionPayloadParser parser = new ExtractionPayloadParser(new ObjectMapper());

    @Test
    void parsesEmbeddedJsonFromDocumentText() {
        String document = """
                Some header
                {
                  "companyName": "ABC Construction Pvt Ltd",
                  "registrationNumber": "U12345DL2020PTC123456",
                  "address": "New Delhi",
                  "annualRevenue": 12500000,
                  "documentDate": "2026-08-15"
                }
                """;

        MockExtractionPayload payload = parser.parse(document);

        assertThat(payload.companyName()).isEqualTo("ABC Construction Pvt Ltd");
        assertThat(payload.registrationNumber()).isEqualTo("U12345DL2020PTC123456");
        assertThat(payload.address()).isEqualTo("New Delhi");
        assertThat(payload.annualRevenue()).isEqualByComparingTo(BigDecimal.valueOf(12_500_000));
        assertThat(payload.documentDate()).hasToString("2026-08-15");
    }

    @Test
    void referenceFingerprintReturnsCanonicalValues() {
        String document = "Registration U12345DL2020PTC123456 for ABC Construction Pvt Ltd";

        MockExtractionPayload payload = parser.parse(document);

        assertThat(payload).isEqualTo(ReferenceExtractionTemplate.CANONICAL);
    }

    @Test
    void missingFieldsProduceEmptyPayload() {
        MockExtractionPayload payload = parser.parse("no structured content");

        assertThat(payload.companyName()).isNull();
        assertThat(payload.registrationNumber()).isNull();
        assertThat(payload.documentDate()).isNull();
    }
}
