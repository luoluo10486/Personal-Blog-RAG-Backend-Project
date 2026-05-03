package com.personalblog.ragbackend.knowledge.core.parser;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class DocumentParserSelector {
    private final List<DocumentParser> parsers;
    private final Map<String, DocumentParser> parserMap;

    public DocumentParserSelector(List<DocumentParser> parsers) {
        this.parsers = List.copyOf(parsers);
        this.parserMap = parsers.stream()
                .collect(Collectors.toUnmodifiableMap(
                        DocumentParser::getParserType,
                        Function.identity(),
                        (existing, replacement) -> existing
                ));
    }

    public DocumentParser requireParser(String parserType) {
        DocumentParser parser = parserMap.get(parserType);
        if (parser != null) {
            return parser;
        }
        throw new IllegalArgumentException("Unknown document parser: " + parserType);
    }

    public DocumentParser select(String mimeType, String fileName) {
        return parsers.stream()
                .filter(parser -> parser.supports(mimeType, fileName))
                .findFirst()
                .orElseGet(this::defaultParser);
    }

    private DocumentParser defaultParser() {
        return parsers.stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No document parser registered"));
    }
}
