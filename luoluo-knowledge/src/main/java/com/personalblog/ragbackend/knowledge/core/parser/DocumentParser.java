package com.personalblog.ragbackend.knowledge.core.parser;

import com.personalblog.ragbackend.knowledge.dto.document.ParseResult;

import java.io.InputStream;

public interface DocumentParser {

    String getParserType();

    ParseResult parse(InputStream stream, String fileName, String declaredMimeType);

    default boolean supports(String mimeType, String fileName) {
        return true;
    }
}
