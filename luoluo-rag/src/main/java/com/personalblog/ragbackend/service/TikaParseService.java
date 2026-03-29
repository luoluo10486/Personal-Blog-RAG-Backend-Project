package com.personalblog.ragbackend.service;

import com.personalblog.ragbackend.dto.document.ParseResult;
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

/**
 * Tika 文档解析服务，负责提取上传文件的正文内容和元数据。
 */
@Service
public class TikaParseService {
    private static final Logger log = LoggerFactory.getLogger(TikaParseService.class);
    private static final int MAX_TEXT_LENGTH = 10 * 1024 * 1024;

    private final Tika tika = new Tika();
    private final Parser parser = new AutoDetectParser();

    /**
     * 解析上传文件，返回正文、MIME 类型和元数据。
     */
    public ParseResult parseFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ParseResult.failure("文件为空");
        }

        String originalFilename = file.getOriginalFilename();
        log.info("开始解析文件: {}, 大小: {} bytes", originalFilename, file.getSize());

        try {
            String mimeType = detectMimeType(file);
            log.info("检测到 MIME 类型: {}", mimeType);

            BodyContentHandler handler = new BodyContentHandler(MAX_TEXT_LENGTH);
            Metadata metadata = new Metadata();
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, originalFilename);

            ParseContext context = new ParseContext();
            try (InputStream parseStream = file.getInputStream()) {
                parser.parse(parseStream, handler, metadata, context);
            }

            String content = cleanText(handler.toString());
            Map<String, String> metadataMap = extractMetadata(metadata);

            if (content.isEmpty()) {
                log.warn("文件 {} 解析结果为空，可能是扫描件或加密文档", originalFilename);
                return ParseResult.failure("解析结果为空，可能是扫描件或加密文档");
            }

            log.info("文件 {} 解析成功，提取文本长度: {}", originalFilename, content.length());
            return ParseResult.success(mimeType, content, metadataMap);
        } catch (IOException exception) {
            log.error("读取文件失败: {}", originalFilename, exception);
            return ParseResult.failure("读取文件失败: " + exception.getMessage());
        } catch (TikaException exception) {
            log.error("Tika 解析失败: {}", originalFilename, exception);
            return ParseResult.failure("文档解析失败: " + exception.getMessage());
        } catch (SAXException exception) {
            log.error("XML 解析失败: {}", originalFilename, exception);
            return ParseResult.failure("文档结构解析失败: " + exception.getMessage());
        } catch (Exception exception) {
            log.error("未知错误: {}", originalFilename, exception);
            return ParseResult.failure("解析过程中发生未知错误: " + exception.getMessage());
        }
    }

    /**
     * 仅检测文件的 MIME 类型，供解析流程内部复用。
     */
    public String detectMimeType(MultipartFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream()) {
            return tika.detect(inputStream, file.getOriginalFilename());
        }
    }

    /**
     * 对提取出的正文做基础清洗，减少无意义空白和多余换行。
     */
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

    /**
     * 将 Tika Metadata 转换为普通 Map，便于接口直接序列化返回。
     */
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
