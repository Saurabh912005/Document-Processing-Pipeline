package com.suretyseven.documentprocessing.service;

import com.suretyseven.documentprocessing.config.DocumentStorageProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileStorageService {

    private final Path root;

    public FileStorageService(DocumentStorageProperties properties) throws IOException {
        this.root = Path.of(properties.path()).toAbsolutePath().normalize();
        Files.createDirectories(this.root);
    }

    public String store(String documentId, MultipartFile file) throws IOException {
        String safeName = sanitizeFilename(file.getOriginalFilename());
        Path target = root.resolve(documentId + "_" + safeName);
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target.toString();
    }

    public Path resolvePath(String storagePath) {
        return Path.of(storagePath);
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "upload.bin";
        }
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
