package com.personalblog.ragbackend.ingestion.strategy.fetcher;

import com.personalblog.ragbackend.ingestion.domain.context.DocumentSource;
import com.personalblog.ragbackend.ingestion.domain.enums.SourceType;
import com.personalblog.ragbackend.ingestion.util.HttpClientHelper;
import com.personalblog.ragbackend.ingestion.util.MimeTypeDetector;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class HttpUrlFetcher implements DocumentFetcher {

    private final HttpClientHelper httpClientHelper;

    @Override
    public SourceType supportedType() {
        return SourceType.URL;
    }

    @Override
    public FetchResult fetch(DocumentSource source) {
        String location = source.getLocation();
        if (!StringUtils.hasText(location)) {
            throw new IllegalArgumentException("url location is required");
        }
        Map<String, String> headers = buildHeaders(source.getCredentials());
        HttpClientHelper.HttpFetchResponse response = httpClientHelper.get(location, headers);
        String fileName = StringUtils.hasText(source.getFileName()) ? source.getFileName() : response.fileName();
        String contentType = normalize(response.contentType());
        if (!StringUtils.hasText(contentType)) {
            contentType = MimeTypeDetector.detect(response.body(), fileName);
        }
        return new FetchResult(response.body(), contentType, fileName);
    }

    private Map<String, String> buildHeaders(Map<String, String> credentials) {
        if (credentials == null || credentials.isEmpty()) {
            return Map.of();
        }
        Map<String, String> headers = new HashMap<>();
        credentials.forEach((key, value) -> {
            if (!StringUtils.hasText(key) || value == null) {
                return;
            }
            if ("token".equalsIgnoreCase(key)) {
                headers.put("Authorization", "Bearer " + value);
            } else {
                headers.put(key, value);
            }
        });
        return headers;
    }

    private String normalize(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return null;
        }
        int idx = contentType.indexOf(';');
        return idx > 0 ? contentType.substring(0, idx).trim() : contentType.trim();
    }
}
