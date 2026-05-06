package com.personalblog.ragbackend.knowledge.service.document;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Service
public class KnowledgeFileStorageService {
    private final Path root = Path.of("data", "knowledge");

    public String store(MultipartFile file, Long kbId, String docName) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        try {
            Files.createDirectories(root);
            Path kbDir = root.resolve(String.valueOf(kbId == null ? "default" : kbId));
            Files.createDirectories(kbDir);
            String safeName = sanitize(docName);
            String suffix = suffixOf(file.getOriginalFilename());
            Path target = kbDir.resolve(System.currentTimeMillis() + "_" + safeName + suffix);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target.toAbsolutePath().toString();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to store knowledge file", exception);
        }
    }

    public MultipartFile restore(String fileUrl, String originalFilename, String contentType) {
        if (!StringUtils.hasText(fileUrl)) {
            return null;
        }
        return new RestoredMultipartFile(Path.of(fileUrl), originalFilename, contentType);
    }

    private String sanitize(String value) {
        if (!StringUtils.hasText(value)) {
            return "uploaded-document";
        }
        return value.trim().replaceAll("[^a-zA-Z0-9._-]+", "_");
    }

    private String suffixOf(String filename) {
        if (!StringUtils.hasText(filename) || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.'));
    }

    private static final class RestoredMultipartFile implements MultipartFile {
        private final Path path;
        private final String originalFilename;
        private final String contentType;

        private RestoredMultipartFile(Path path, String originalFilename, String contentType) {
            this.path = path;
            this.originalFilename = StringUtils.hasText(originalFilename)
                    ? originalFilename
                    : path.getFileName().toString();
            this.contentType = contentType;
        }

        @Override
        public String getName() {
            return originalFilename;
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return !Files.exists(path);
        }

        @Override
        public long getSize() {
            try {
                return Files.size(path);
            } catch (IOException exception) {
                return 0L;
            }
        }

        @Override
        public byte[] getBytes() throws IOException {
            return Files.readAllBytes(path);
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return Files.newInputStream(path);
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
            Files.copy(path, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
