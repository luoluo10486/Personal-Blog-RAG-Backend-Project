package com.personalblog.ragbackend.service;

import com.personalblog.ragbackend.dto.document.DocumentChunk;
import com.personalblog.ragbackend.dto.document.DocumentChunkResponse;
import com.personalblog.ragbackend.dto.document.ParseResult;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 文档分块（chunk）服务：优先按结构（标题/列表/表格/代码块）分段，再按长度策略合并与重叠切分。
 */
@Service
public class DocumentChunkService {
    static final int TARGET_CHUNK_SIZE = 700;
    static final int MAX_CHUNK_SIZE = 1100;
    static final int OVERLAP_SIZE = 120;

    private static final Pattern MARKDOWN_HEADING = Pattern.compile("^\\s{0,3}#{1,6}\\s+.+$");
    private static final Pattern CN_SECTION_HEADING = Pattern.compile(
            "^\\s*(第[一二三四五六七八九十百千万0-9]+[章节部分篇]|[一二三四五六七八九十]+[、.．]|\\d+(?:\\.\\d+)*[、.．])\\s*.+$"
    );
    private static final Pattern LIST_ITEM = Pattern.compile("^\\s*(?:[-*+]\\s+|\\d+[.)]\\s+|[一二三四五六七八九十]+[、.．]\\s+).+$");
    private static final Pattern TABLE_LINE = Pattern.compile("^\\s*\\|.*\\|\\s*$");

    private final TikaParseService tikaParseService;

    public DocumentChunkService(TikaParseService tikaParseService) {
        this.tikaParseService = tikaParseService;
    }

    public DocumentChunkResponse chunkFile(MultipartFile file) {
        ParseResult parseResult = tikaParseService.parseFile(file);
        if (!parseResult.success()) {
            return DocumentChunkResponse.failure(parseResult.errorMessage());
        }

        List<ContentBlock> blocks = extractBlocks(parseResult.content());
        if (blocks.isEmpty() && parseResult.content() != null && !parseResult.content().isBlank()) {
            blocks = List.of(new ContentBlock(null, parseResult.content().trim()));
        }

        List<DocumentChunk> chunks = buildChunks(blocks);
        return DocumentChunkResponse.success(
                parseResult.mimeType(),
                parseResult.metadata(),
                parseResult.contentLength(),
                TARGET_CHUNK_SIZE,
                MAX_CHUNK_SIZE,
                OVERLAP_SIZE,
                chunks
        );
    }

    List<DocumentChunk> buildChunks(List<ContentBlock> blocks) {
        List<DocumentChunk> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String currentSection = null;
        String lastFinalizedContent = null;
        boolean overlapFromPrevious = false;

        for (ContentBlock block : normalizeBlocks(blocks)) {
            if (current.isEmpty()) {
                current.append(block.content());
                currentSection = block.sectionTitle();
                overlapFromPrevious = false;
                continue;
            }

            String separator = "\n\n";
            boolean sameSection = Objects.equals(currentSection, block.sectionTitle());
            int mergedLength = current.length() + separator.length() + block.content().length();
            boolean shouldMerge = mergedLength <= TARGET_CHUNK_SIZE
                    || (mergedLength <= MAX_CHUNK_SIZE && current.length() < TARGET_CHUNK_SIZE / 2);

            if (sameSection && shouldMerge) {
                current.append(separator).append(block.content());
                continue;
            }

            String finalized = current.toString().trim();
            chunks.add(new DocumentChunk(
                    chunks.size() + 1,
                    currentSection,
                    finalized,
                    finalized.length(),
                    overlapFromPrevious
            ));
            lastFinalizedContent = finalized;

            current.setLength(0);
            currentSection = block.sectionTitle();
            overlapFromPrevious = false;

            if (sameSection) {
                String overlapText = tailOverlap(lastFinalizedContent);
                if (!overlapText.isBlank() && overlapText.length() + 1 + block.content().length() <= MAX_CHUNK_SIZE) {
                    current.append(overlapText).append('\n').append(block.content());
                    overlapFromPrevious = true;
                } else {
                    current.append(block.content());
                }
            } else {
                current.append(block.content());
            }
        }

        if (!current.isEmpty()) {
            String finalized = current.toString().trim();
            chunks.add(new DocumentChunk(
                    chunks.size() + 1,
                    currentSection,
                    finalized,
                    finalized.length(),
                    overlapFromPrevious
            ));
        }

        return chunks;
    }

    List<ContentBlock> extractBlocks(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        String normalized = content.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        List<ContentBlock> blocks = new ArrayList<>();
        String currentSection = null;
        int index = 0;

        while (index < lines.length) {
            String line = lines[index];
            String trimmed = line.trim();

            if (trimmed.isEmpty()) {
                index++;
                continue;
            }

            if (isHeading(trimmed)) {
                currentSection = normalizeHeading(trimmed);
                index++;
                continue;
            }

            if (isFenceStart(trimmed)) {
                int end = index + 1;
                while (end < lines.length && !isFenceStart(lines[end].trim())) {
                    end++;
                }
                if (end < lines.length) {
                    end++;
                }
                blocks.add(new ContentBlock(currentSection, joinLines(lines, index, end)));
                index = end;
                continue;
            }

            if (isTableLine(trimmed)) {
                int end = index + 1;
                while (end < lines.length && isTableLine(lines[end].trim())) {
                    end++;
                }
                blocks.add(new ContentBlock(currentSection, joinLines(lines, index, end)));
                index = end;
                continue;
            }

            if (isListItem(trimmed)) {
                int end = index + 1;
                while (end < lines.length) {
                    String nextTrimmed = lines[end].trim();
                    if (nextTrimmed.isEmpty() || isHeading(nextTrimmed) || isFenceStart(nextTrimmed)) {
                        break;
                    }
                    if (!isListItem(nextTrimmed) && !isIndentedContinuation(lines[end])) {
                        break;
                    }
                    end++;
                }
                blocks.add(new ContentBlock(currentSection, joinLines(lines, index, end)));
                index = end;
                continue;
            }

            int end = index + 1;
            while (end < lines.length) {
                String nextTrimmed = lines[end].trim();
                if (nextTrimmed.isEmpty()
                        || isHeading(nextTrimmed)
                        || isFenceStart(nextTrimmed)
                        || isListItem(nextTrimmed)
                        || isTableLine(nextTrimmed)) {
                    break;
                }
                end++;
            }
            blocks.add(new ContentBlock(currentSection, joinLines(lines, index, end)));
            index = end;
        }

        return blocks;
    }

    private List<ContentBlock> normalizeBlocks(List<ContentBlock> rawBlocks) {
        List<ContentBlock> normalized = new ArrayList<>();
        for (ContentBlock block : rawBlocks) {
            String content = block.content() == null ? "" : block.content().trim();
            if (content.isEmpty()) {
                continue;
            }
            if (content.length() <= MAX_CHUNK_SIZE) {
                normalized.add(new ContentBlock(block.sectionTitle(), content));
                continue;
            }
            for (String split : splitLargeBlock(content)) {
                if (!split.isBlank()) {
                    normalized.add(new ContentBlock(block.sectionTitle(), split.trim()));
                }
            }
        }
        return normalized;
    }

    private List<String> splitLargeBlock(String text) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int tentativeEnd = Math.min(start + MAX_CHUNK_SIZE, text.length());
            int end = tentativeEnd;
            if (tentativeEnd < text.length()) {
                int boundary = findPreferredBoundary(text, start, tentativeEnd);
                if (boundary > start + MAX_CHUNK_SIZE / 2) {
                    end = boundary;
                }
            }

            String part = text.substring(start, end).trim();
            if (!part.isEmpty()) {
                chunks.add(part);
            }
            if (end >= text.length()) {
                break;
            }

            start = Math.max(end - OVERLAP_SIZE, start + 1);
            while (start < text.length() && Character.isWhitespace(text.charAt(start))) {
                start++;
            }
        }
        return chunks;
    }

    private int findPreferredBoundary(String text, int start, int end) {
        int boundary = findLast(text, "\n\n", start, end);
        if (boundary != -1) {
            return boundary + 2;
        }
        boundary = findLast(text, "\n", start, end);
        if (boundary != -1) {
            return boundary + 1;
        }

        for (int index = end - 1; index > start; index--) {
            char current = text.charAt(index);
            if (current == '。' || current == '！' || current == '？'
                    || current == '!' || current == '?' || current == ';' || current == '；'
                    || current == '.') {
                return index + 1;
            }
        }

        for (int index = end - 1; index > start; index--) {
            if (Character.isWhitespace(text.charAt(index))) {
                return index + 1;
            }
        }

        return end;
    }

    private int findLast(String text, String target, int start, int end) {
        int searchFrom = Math.max(start, 0);
        int match = text.lastIndexOf(target, end - 1);
        return match >= searchFrom ? match : -1;
    }

    private String tailOverlap(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        if (content.length() <= OVERLAP_SIZE) {
            return content.trim();
        }
        return content.substring(content.length() - OVERLAP_SIZE).trim();
    }

    private boolean isHeading(String line) {
        return MARKDOWN_HEADING.matcher(line).matches() || CN_SECTION_HEADING.matcher(line).matches();
    }

    private String normalizeHeading(String line) {
        if (line == null) {
            return null;
        }
        String trimmed = line.trim();
        if (trimmed.startsWith("#")) {
            return trimmed.replaceFirst("^#+\\s*", "").trim();
        }
        return trimmed;
    }

    private boolean isFenceStart(String line) {
        return line.startsWith("```") || line.startsWith("~~~");
    }

    private boolean isListItem(String line) {
        return LIST_ITEM.matcher(line).matches();
    }

    private boolean isTableLine(String line) {
        return TABLE_LINE.matcher(line).matches();
    }

    private boolean isIndentedContinuation(String line) {
        return line.startsWith("  ") || line.startsWith("\t");
    }

    private String joinLines(String[] lines, int startInclusive, int endExclusive) {
        StringBuilder builder = new StringBuilder();
        for (int index = startInclusive; index < endExclusive; index++) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(lines[index]);
        }
        return builder.toString().trim();
    }

    record ContentBlock(String sectionTitle, String content) {
    }
}
