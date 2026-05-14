package com.personalblog.ragbackend.ingestion.util;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

@Component
public class HttpClientHelper {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public HttpFetchResponse get(String url, Map<String, String> headers) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).GET();
            if (headers != null) {
                headers.forEach((key, value) -> {
                    if (key != null && value != null) {
                        builder.header(key, value);
                    }
                });
            }
            HttpResponse<byte[]> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            String contentType = response.headers().firstValue("content-type").orElse(null);
            String fileName = resolveFileName(response);
            return new HttpFetchResponse(response.body(), contentType, fileName);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("HTTP request interrupted", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("HTTP request failed", exception);
        }
    }

    private String resolveFileName(HttpResponse<byte[]> response) {
        return response.headers().firstValue("content-disposition")
                .map(value -> {
                    int index = value.toLowerCase().indexOf("filename=");
                    if (index < 0) {
                        return null;
                    }
                    String filename = value.substring(index + 9).trim();
                    if (filename.startsWith("\"") && filename.endsWith("\"") && filename.length() > 1) {
                        filename = filename.substring(1, filename.length() - 1);
                    }
                    return filename;
                })
                .orElse(null);
    }

    public record HttpFetchResponse(byte[] body, String contentType, String fileName) {
    }
}
