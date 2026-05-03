package com.personalblog.ragbackend.knowledge.application;

import com.personalblog.ragbackend.knowledge.core.parser.DocumentParser;
import com.personalblog.ragbackend.knowledge.core.parser.DocumentParserSelector;
import com.personalblog.ragbackend.knowledge.dto.document.DocumentChunkResponse;
import com.personalblog.ragbackend.knowledge.dto.document.ParseResult;
import com.personalblog.ragbackend.knowledge.service.document.KnowledgeDocumentChunkService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

@Service
public class KnowledgeDocumentApplicationService {
    private static final String DEFAULT_MIME_TYPE = "application/octet-stream";

    private final DocumentParserSelector documentParserSelector;
    private final KnowledgeDocumentChunkService knowledgeDocumentChunkService;

    public KnowledgeDocumentApplicationService(DocumentParserSelector documentParserSelector,
                                               KnowledgeDocumentChunkService knowledgeDocumentChunkService) {
        this.documentParserSelector = documentParserSelector;
        this.knowledgeDocumentChunkService = knowledgeDocumentChunkService;
    }

    public ParseResult parseFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ParseResult.failure("文件为空");
        }

        String fileName = file.getOriginalFilename();
        String declaredMimeType = resolveDeclaredMimeType(file);
        DocumentParser parser = documentParserSelector.select(declaredMimeType, fileName);

        try (InputStream inputStream = file.getInputStream()) {
            return parser.parse(inputStream, fileName, declaredMimeType);
        } catch (IOException exception) {
            return ParseResult.failure("读取文件失败: " + exception.getMessage());
        }
    }

    public DocumentChunkResponse chunkFile(MultipartFile file) {
        return knowledgeDocumentChunkService.chunkParsedResult(parseFile(file));
    }

    public DocumentChunkResponse chunkText(String content) {
        return knowledgeDocumentChunkService.chunkText(content);
    }

    private String resolveDeclaredMimeType(MultipartFile file) {
        if (file.getContentType() == null || file.getContentType().isBlank()) {
            return DEFAULT_MIME_TYPE;
        }
        return file.getContentType().trim();
    }
}
