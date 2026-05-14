package com.personalblog.ragbackend.ingestion.node;

import com.personalblog.ragbackend.ingestion.domain.context.DocumentSource;
import com.personalblog.ragbackend.ingestion.domain.context.IngestionContext;
import com.personalblog.ragbackend.ingestion.domain.enums.IngestionNodeType;
import com.personalblog.ragbackend.ingestion.domain.pipeline.NodeConfig;
import com.personalblog.ragbackend.ingestion.domain.result.NodeResult;
import com.personalblog.ragbackend.ingestion.strategy.fetcher.DocumentFetcher;
import com.personalblog.ragbackend.ingestion.strategy.fetcher.FetchResult;
import com.personalblog.ragbackend.ingestion.util.MimeTypeDetector;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class FetcherNode implements IngestionNode {

    private final Map<com.personalblog.ragbackend.ingestion.domain.enums.SourceType, DocumentFetcher> fetchers;

    public FetcherNode(List<DocumentFetcher> fetchers) {
        this.fetchers = fetchers.stream().collect(Collectors.toMap(DocumentFetcher::supportedType, Function.identity(), (left, right) -> left));
    }

    @Override
    public String getNodeType() {
        return IngestionNodeType.FETCHER.getValue();
    }

    @Override
    public NodeResult execute(IngestionContext context, NodeConfig config) {
        if (context.getRawBytes() != null && context.getRawBytes().length > 0) {
            if (!StringUtils.hasText(context.getMimeType())) {
                String fileName = context.getSource() == null ? null : context.getSource().getFileName();
                context.setMimeType(MimeTypeDetector.detect(context.getRawBytes(), fileName));
            }
            return NodeResult.ok("source already fetched");
        }

        DocumentSource source = context.getSource();
        if (source == null || source.getType() == null) {
            return NodeResult.fail(new IllegalArgumentException("document source is required"));
        }

        DocumentFetcher fetcher = fetchers.get(source.getType());
        if (fetcher == null) {
            return NodeResult.fail(new IllegalArgumentException("unsupported source type: " + source.getType()));
        }

        FetchResult result = fetcher.fetch(source);
        context.setRawBytes(result.content());
        if (StringUtils.hasText(result.mimeType())) {
            context.setMimeType(result.mimeType());
        }
        if (StringUtils.hasText(result.fileName())) {
            source.setFileName(result.fileName());
        }
        return NodeResult.ok("fetched " + (result.content() == null ? 0 : result.content().length) + " bytes");
    }
}
