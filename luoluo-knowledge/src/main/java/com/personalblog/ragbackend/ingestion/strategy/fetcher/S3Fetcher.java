package com.personalblog.ragbackend.ingestion.strategy.fetcher;

import com.personalblog.ragbackend.ingestion.domain.context.DocumentSource;
import com.personalblog.ragbackend.ingestion.domain.enums.SourceType;
import com.personalblog.ragbackend.ingestion.util.MimeTypeDetector;
import com.personalblog.ragbackend.rag.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.InputStream;

@Component
@RequiredArgsConstructor
public class S3Fetcher implements DocumentFetcher {

    private final FileStorageService fileStorageService;

    @Override
    public SourceType supportedType() {
        return SourceType.S3;
    }

    @Override
    public FetchResult fetch(DocumentSource source) {
        String location = source.getLocation();
        if (!StringUtils.hasText(location)) {
            throw new IllegalArgumentException("s3 location is required");
        }
        if (!location.startsWith("s3://")) {
            throw new IllegalArgumentException("invalid s3 location: " + location);
        }
        try (InputStream inputStream = fileStorageService.openStream(location)) {
            byte[] bytes = inputStream.readAllBytes();
            String fileName = StringUtils.hasText(source.getFileName()) ? source.getFileName() : extractFileName(location);
            String mimeType = MimeTypeDetector.detect(bytes, fileName);
            return new FetchResult(bytes, mimeType, fileName);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to read s3 file", ex);
        }
    }

    private String extractFileName(String location) {
        int idx = location.lastIndexOf('/');
        return idx >= 0 ? location.substring(idx + 1) : location;
    }
}
