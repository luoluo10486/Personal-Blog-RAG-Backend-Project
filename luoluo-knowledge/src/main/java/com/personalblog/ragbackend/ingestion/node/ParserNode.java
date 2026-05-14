package com.personalblog.ragbackend.ingestion.node;

import com.personalblog.ragbackend.ingestion.domain.context.IngestionContext;
import com.personalblog.ragbackend.ingestion.domain.context.StructuredDocument;
import com.personalblog.ragbackend.ingestion.domain.enums.IngestionNodeType;
import com.personalblog.ragbackend.ingestion.domain.pipeline.NodeConfig;
import com.personalblog.ragbackend.ingestion.domain.result.NodeResult;
import com.personalblog.ragbackend.ingestion.domain.settings.ParserSettings;
import com.personalblog.ragbackend.ingestion.util.MimeTypeDetector;
import com.personalblog.ragbackend.knowledge.core.parser.DocumentParser;
import com.personalblog.ragbackend.knowledge.core.parser.DocumentParserSelector;
import com.personalblog.ragbackend.knowledge.dto.document.ParseResult;
import com.personalblog.ragbackend.knowledge.service.document.TikaDocumentParseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class ParserNode implements IngestionNode {

    private final ObjectMapper objectMapper;
    private final DocumentParserSelector parserSelector;

    public ParserNode(ObjectMapper objectMapper, DocumentParserSelector parserSelector) {
        this.objectMapper = objectMapper;
        this.parserSelector = parserSelector;
    }

    @Override
    public String getNodeType() {
        return IngestionNodeType.PARSER.getValue();
    }

    @Override
    public NodeResult execute(IngestionContext context, NodeConfig config) {
        if (context.getRawBytes() == null || context.getRawBytes().length == 0) {
            return NodeResult.fail(new IllegalArgumentException("raw bytes are required"));
        }
        String mimeType = context.getMimeType();
        if (!StringUtils.hasText(mimeType)) {
            String fileName = context.getSource() == null ? null : context.getSource().getFileName();
            mimeType = MimeTypeDetector.detect(context.getRawBytes(), fileName);
            context.setMimeType(mimeType);
        }

        ParserSettings settings = parseSettings(config.getSettings());
        String fileName = context.getSource() == null ? null : context.getSource().getFileName();
        validateMimeType(settings, mimeType, fileName);
        ParserSettings.ParserRule rule = matchRule(settings, mimeType, fileName);

        DocumentParser parser = parserSelector.select(mimeType, fileName);
        if (parser == null) {
            parser = parserSelector.requireParser(TikaDocumentParseService.PARSER_TYPE);
        }

        Map<String, Object> options = rule == null || rule.getOptions() == null ? Collections.emptyMap() : rule.getOptions();
        ParseResult result = parser.parse(new ByteArrayInputStream(context.getRawBytes()), fileName, mimeType);
        context.setRawText(result.content());
        context.setDocument(StructuredDocument.builder()
                .text(result.content())
                .metadata(options.isEmpty() ? Map.of() : options)
                .build());
        return NodeResult.ok("parsed " + (result.content() == null ? 0 : result.content().length()) + " chars");
    }

    private void validateMimeType(ParserSettings settings, String mimeType, String fileName) {
        if (settings == null || settings.getRules() == null || settings.getRules().isEmpty()) {
            return;
        }
        String resolvedType = resolveType(mimeType, fileName);
        boolean hasMatch = false;
        for (ParserSettings.ParserRule rule : settings.getRules()) {
            if (rule == null || !StringUtils.hasText(rule.getMimeType())) {
                continue;
            }
            String configured = normalizeType(rule.getMimeType());
            if ("ALL".equals(configured) || configured.equalsIgnoreCase(resolvedType)) {
                hasMatch = true;
                break;
            }
        }
        if (!hasMatch) {
            throw new IllegalArgumentException("unsupported mime type: " + resolvedType);
        }
    }

    private ParserSettings parseSettings(com.fasterxml.jackson.databind.JsonNode node) {
        if (node == null || node.isNull()) {
            return ParserSettings.builder().rules(List.of()).build();
        }
        return objectMapper.convertValue(node, ParserSettings.class);
    }

    private ParserSettings.ParserRule matchRule(ParserSettings settings, String mimeType, String fileName) {
        if (settings == null || settings.getRules() == null || settings.getRules().isEmpty()) {
            return null;
        }
        String resolvedType = resolveType(mimeType, fileName);
        for (ParserSettings.ParserRule rule : settings.getRules()) {
            if (rule == null || !StringUtils.hasText(rule.getMimeType())) {
                continue;
            }
            String configured = normalizeType(rule.getMimeType());
            if ("ALL".equals(configured) || configured.equalsIgnoreCase(resolvedType)) {
                return rule;
            }
        }
        return null;
    }

    private String resolveType(String mimeType, String fileName) {
        if (StringUtils.hasText(fileName)) {
            String lower = fileName.toLowerCase();
            if (lower.endsWith(".pdf")) return "PDF";
            if (lower.endsWith(".md") || lower.endsWith(".markdown")) return "MARKDOWN";
            if (lower.endsWith(".doc") || lower.endsWith(".docx")) return "WORD";
            if (lower.endsWith(".xls") || lower.endsWith(".xlsx")) return "EXCEL";
            if (lower.endsWith(".ppt") || lower.endsWith(".pptx")) return "PPT";
            if (lower.endsWith(".txt")) return "TEXT";
        }
        if (!StringUtils.hasText(mimeType)) {
            return "UNKNOWN";
        }
        String lower = mimeType.trim().toLowerCase();
        if (lower.contains("pdf")) return "PDF";
        if (lower.contains("markdown")) return "MARKDOWN";
        if (lower.contains("word") || lower.contains("msword")) return "WORD";
        if (lower.contains("excel") || lower.contains("spreadsheetml")) return "EXCEL";
        if (lower.contains("powerpoint") || lower.contains("presentation")) return "PPT";
        if (lower.startsWith("text/")) return "TEXT";
        return "UNKNOWN";
    }

    private String normalizeType(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String value = raw.trim().toUpperCase();
        return switch (value) {
            case "*", "ALL", "DEFAULT" -> "ALL";
            case "MD", "MARKDOWN" -> "MARKDOWN";
            case "DOC", "DOCX", "WORD" -> "WORD";
            case "XLS", "XLSX", "EXCEL" -> "EXCEL";
            case "PPT", "PPTX", "POWERPOINT" -> "PPT";
            case "TXT", "TEXT" -> "TEXT";
            case "PDF" -> "PDF";
            default -> value;
        };
    }
}
