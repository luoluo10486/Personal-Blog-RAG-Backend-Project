package com.personalblog.ragbackend.knowledge.service.document;

import com.personalblog.ragbackend.knowledge.dto.document.ParseResult;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TikaDocumentParseServiceTest {

    private final TikaDocumentParseService tikaDocumentParseService = new TikaDocumentParseService();

    @Test
    void parseFileShouldExtractPlainTextContent() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                "Hello World".getBytes()
        );

        ParseResult result = tikaDocumentParseService.parseFile(file);

        assertTrue(result.success());
        assertEquals("text/plain", result.mimeType());
        assertEquals("Hello World", result.content());
        assertEquals(11, result.contentLength());
        assertNull(result.errorMessage());
        assertTrue(result.metadata().containsKey("resourceName"));
        assertEquals("test.txt", result.metadata().get("resourceName"));
    }

    @Test
    void parseFileShouldRejectEmptyFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "empty.txt",
                "text/plain",
                new byte[0]
        );

        ParseResult result = tikaDocumentParseService.parseFile(file);

        assertFalse(result.success());
        assertEquals(0, result.contentLength());
        assertEquals("文件为空", result.errorMessage());
    }
}
