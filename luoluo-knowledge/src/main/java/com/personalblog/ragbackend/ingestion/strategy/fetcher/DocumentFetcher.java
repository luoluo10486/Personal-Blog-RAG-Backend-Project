package com.personalblog.ragbackend.ingestion.strategy.fetcher;

import com.personalblog.ragbackend.ingestion.domain.context.DocumentSource;
import com.personalblog.ragbackend.ingestion.domain.enums.SourceType;

public interface DocumentFetcher {
    SourceType supportedType();

    FetchResult fetch(DocumentSource source);
}
