package com.personalblog.ragbackend.knowledge.service.ingest.node;

import com.personalblog.ragbackend.knowledge.core.parser.DocumentParser;
import com.personalblog.ragbackend.knowledge.core.parser.DocumentParserSelector;
import com.personalblog.ragbackend.knowledge.dto.document.ParseResult;
import com.personalblog.ragbackend.knowledge.service.ingest.KnowledgeIngestionContext;
import com.personalblog.ragbackend.knowledge.service.ingest.KnowledgeIngestionNode;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

@Component
public class ParseIngestionNode implements KnowledgeIngestionNode {
    private static final String DEFAULT_MIME_TYPE = "application/octet-stream";

    private final DocumentParserSelector documentParserSelector;

    public ParseIngestionNode(DocumentParserSelector documentParserSelector) {
        this.documentParserSelector = documentParserSelector;
    }

    @Override
    public String getNodeType() {
        return "parse";
    }

    @Override
    public int getOrder() {
        return 20;
    }

    @Override
    public void execute(KnowledgeIngestionContext context) {
        if (context.isPlanOnly()) {
            return;
        }

        MultipartFile file = context.getFile();
        if (file == null || file.isEmpty()) {
            context.setParseResult(ParseResult.failure("鏂囦欢涓虹┖"));
            return;
        }

        String fileName = file.getOriginalFilename();
        String declaredMimeType = resolveDeclaredMimeType(file);
        DocumentParser parser = documentParserSelector.select(declaredMimeType, fileName);

        try (InputStream inputStream = file.getInputStream()) {
            context.setParseResult(parser.parse(inputStream, fileName, declaredMimeType));
        } catch (IOException exception) {
            context.setParseResult(ParseResult.failure("璇诲彇鏂囦欢澶辫触: " + exception.getMessage()));
        }
    }

    private String resolveDeclaredMimeType(MultipartFile file) {
        if (file.getContentType() == null || file.getContentType().isBlank()) {
            return DEFAULT_MIME_TYPE;
        }
        return file.getContentType().trim();
    }
}
