package com.personalblog.ragbackend.ingestion.strategy.fetcher;

public record FetchResult(byte[] content, String mimeType, String fileName) {
}
