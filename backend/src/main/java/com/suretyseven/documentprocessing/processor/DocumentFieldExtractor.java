package com.suretyseven.documentprocessing.processor;

import com.suretyseven.documentprocessing.domain.Document;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DocumentFieldExtractor {

    private static final Logger log = LoggerFactory.getLogger(DocumentFieldExtractor.class);

    private final ExtractionPayloadParser payloadParser;

    public DocumentFieldExtractor(ExtractionPayloadParser payloadParser) {
        this.payloadParser = payloadParser;
    }

    public MockExtractionPayload extract(Document document) throws IOException {
        Path path = Path.of(document.getStoragePath());
        byte[] bytes = Files.readAllBytes(path);
        String text = extractText(bytes, document.getFilename());
        log.info(
                "document_extraction documentId={} textLength={} referenceMatch={}",
                document.getId(),
                text.length(),
                ReferenceExtractionTemplate.matchesReference(text));
        return payloadParser.parse(text);
    }

    private String extractText(byte[] bytes, String filename) throws IOException {
        if (looksLikePdf(bytes, filename)) {
            try {
                return extractPdfText(bytes);
            } catch (IOException e) {
                log.warn("document_extraction pdfParseFailed filename={} fallback=plainText", filename);
            }
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static boolean looksLikePdf(byte[] bytes, String filename) {
        if (filename != null && filename.toLowerCase().endsWith(".pdf")) {
            return true;
        }
        return bytes.length >= 4
                && bytes[0] == '%'
                && bytes[1] == 'P'
                && bytes[2] == 'D'
                && bytes[3] == 'F';
    }

    private static String extractPdfText(byte[] bytes) throws IOException {
        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(pdf);
        }
    }
}
