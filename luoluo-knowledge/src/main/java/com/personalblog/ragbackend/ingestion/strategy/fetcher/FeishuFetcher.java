package com.personalblog.ragbackend.ingestion.strategy.fetcher;

import com.personalblog.ragbackend.ingestion.domain.context.DocumentSource;
import com.personalblog.ragbackend.ingestion.domain.enums.SourceType;
import org.springframework.stereotype.Component;

@Component
public class FeishuFetcher implements DocumentFetcher {

    @Override
    public SourceType supportedType() {
        return SourceType.FEISHU;
    }

    @Override
    public FetchResult fetch(DocumentSource source) {
        throw new UnsupportedOperationException("Feishu fetch is not implemented in local alignment");
    }
}
