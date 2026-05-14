package com.personalblog.ragbackend.ingestion.strategy.fetcher;

import com.personalblog.ragbackend.ingestion.domain.context.DocumentSource;
import com.personalblog.ragbackend.ingestion.domain.enums.SourceType;
import com.personalblog.ragbackend.ingestion.util.MimeTypeDetector;
import com.personalblog.ragbackend.rag.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
@RequiredArgsConstructor
public class LocalFileFetcher implements DocumentFetcher {

    private final FileStorageService fileStorageService;

    @Override
    public SourceType supportedType() {
        return SourceType.FILE;
    }

    @Override
    public FetchResult fetch(DocumentSource source) {
        String location = source.getLocation();
        if (!StringUtils.hasText(location)) {
            throw new IllegalArgumentException("file location is required");
        }
        try {
            byte[] bytes;
            String fileName = source.getFileName();
            if (location.startsWith("s3://")) {
                try (InputStream inputStream = fileStorageService.openStream(location)) {
                    bytes = inputStream.readAllBytes();
                }
                if (!StringUtils.hasText(fileName)) {
                    fileName = extractFileName(location);
                }
            } else {
                Path path = location.startsWith("file://") ? Path.of(URI.create(location)) : Path.of(location);
                bytes = Files.readAllBytes(path);
                if (!StringUtils.hasText(fileName) && path.getFileName() != null) {
                    fileName = path.getFileName().toString();
                }
            }
            String mimeType = MimeTypeDetector.detect(bytes, fileName);
            return new FetchResult(bytes, mimeType, fileName);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to read file", ex);
        }
    }

    private String extractFileName(String location) {
        int idx = location.lastIndexOf('/');
        return idx >= 0 ? location.substring(idx + 1) : location;
    }
}
