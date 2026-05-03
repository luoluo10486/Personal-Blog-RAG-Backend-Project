package com.personalblog.ragbackend.knowledge.core.chunk.strategy;

import com.personalblog.ragbackend.knowledge.core.chunk.ChunkingMode;
import com.personalblog.ragbackend.knowledge.core.chunk.ChunkingStrategy;
import com.personalblog.ragbackend.knowledge.core.chunk.TextChunkingOptions;
import com.personalblog.ragbackend.knowledge.dto.document.DocumentChunk;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class FixedSizeChunkingStrategy implements ChunkingStrategy {

    @Override
    public ChunkingMode getType() {
        return ChunkingMode.FIXED_SIZE;
    }

    @Override
    public List<DocumentChunk> chunk(String text, TextChunkingOptions options) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        String normalized = text.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (normalized.isEmpty()) {
            return List.of();
        }

        List<DocumentChunk> chunks = new ArrayList<>();
        int start = 0;
        while (start < normalized.length() && chunks.size() < options.maxChunkCount()) {
            int tentativeEnd = Math.min(start + options.targetChunkSize(), normalized.length());
            int end = tentativeEnd;
            if (tentativeEnd < normalized.length()) {
                end = findPreferredBoundary(normalized, start, Math.min(start + options.maxChunkSize(), normalized.length()));
            }

            String content = normalized.substring(start, end).trim();
            if (!content.isEmpty()) {
                chunks.add(new DocumentChunk(
                        chunks.size() + 1,
                        null,
                        content,
                        content.length(),
                        start > 0 && options.overlapSize() > 0
                ));
            }

            if (end >= normalized.length()) {
                break;
            }

            start = Math.max(end - options.overlapSize(), start + 1);
            while (start < normalized.length() && Character.isWhitespace(normalized.charAt(start))) {
                start++;
            }
        }

        return chunks;
    }

    private int findPreferredBoundary(String text, int start, int end) {
        int paragraph = text.lastIndexOf("\n\n", end - 1);
        if (paragraph >= start) {
            return paragraph + 2;
        }

        int lineBreak = text.lastIndexOf('\n', end - 1);
        if (lineBreak >= start) {
            return lineBreak + 1;
        }

        for (int index = end - 1; index > start; index--) {
            if (Character.isWhitespace(text.charAt(index))) {
                return index + 1;
            }
        }

        return end;
    }
}
