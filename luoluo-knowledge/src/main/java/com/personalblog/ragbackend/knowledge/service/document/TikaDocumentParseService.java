package com.personalblog.ragbackend.knowledge.service.document;

import com.personalblog.ragbackend.knowledge.dto.document.ParseResult;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

@Service
public class TikaDocumentParseService {
    private static final Logger log = LoggerFactory.getLogger(TikaDocumentParseService.class);
    private static final int MAX_TEXT_LENGTH = 10 * 1024 * 1024;

    private final Tika tika = new Tika();
    private final Parser parser = new AutoDetectParser();

    public ParseResult parseFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ParseResult.failure("文件为空");
        }

        String originalFilename = file.getOriginalFilename();
        log.info("开始解析知识库文档: {}, size={} bytes", originalFilename, file.getSize());

        try {
            String mimeType = detectMimeType(file);
            BodyContentHandler handler = new BodyContentHandler(MAX_TEXT_LENGTH);
            Metadata metadata = new Metadata();
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, originalFilename);

            try (InputStream parseStream = file.getInputStream()) {
                parser.parse(parseStream, handler, metadata, new ParseContext());
            }

            String content = cleanText(handler.toString());
            Map<String, String> metadataMap = extractMetadata(metadata);
            if (content.isEmpty()) {
                return ParseResult.failure("解析结果为空，可能是扫描件、加密文件或纯图片文档");
            }

            return ParseResult.success(mimeType, content, metadataMap);
        } catch (IOException ex) {
            log.error("读取知识库文档失败: {}", originalFilename, ex);
            return ParseResult.failure("读取文件失败: " + ex.getMessage());
        } catch (TikaException ex) {
            log.error("Tika 解析知识库文档失败: {}", originalFilename, ex);
            return ParseResult.failure("文档解析失败: " + ex.getMessage());
        } catch (SAXException ex) {
            log.error("XML 结构解析失败: {}", originalFilename, ex);
            return ParseResult.failure("文档结构解析失败: " + ex.getMessage());
        } catch (Exception ex) {
            log.error("解析知识库文档时出现未知错误: {}", originalFilename, ex);
            return ParseResult.failure("解析过程中出现未知错误: " + ex.getMessage());
        }
    }

    public String detectMimeType(MultipartFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream()) {
            return tika.detect(inputStream, file.getOriginalFilename());
        }
    }

    private String cleanText(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replaceAll("\\r\\n", "\n")
                .replaceAll("\\r", "\n")
                .replaceAll("(?m)^[ \\t]+|[ \\t]+$", "")
                .replaceAll("\\n{3,}", "\n\n")
                .replaceAll("[ \\t]+", " ")
                .trim();
    }

    private Map<String, String> extractMetadata(Metadata metadata) {
        Map<String, String> result = new HashMap<>();
        for (String name : metadata.names()) {
            String value = metadata.get(name);
            if (value != null && !value.isEmpty()) {
                result.put(name, value);
            }
        }
        return result;
    }
}
