package com.personalblog.ragbackend.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Small tokenizer shared by sparse retrieval and local rerank fallback.
 */
final class TextTokenUtils {
    private TextTokenUtils() {
    }

    static List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<String> tokens = new ArrayList<>();
        StringBuilder latinBuffer = new StringBuilder();
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (Character.isLetterOrDigit(current)) {
                latinBuffer.append(Character.toLowerCase(current));
                continue;
            }

            flushLatinBuffer(tokens, latinBuffer);
            if (isCjk(current)) {
                tokens.add(String.valueOf(current));
            }
        }
        flushLatinBuffer(tokens, latinBuffer);
        return tokens;
    }

    static Set<String> extractIdentifierTokens(String text) {
        Set<String> identifiers = new LinkedHashSet<>();
        for (String token : tokenize(text)) {
            if (token.length() >= 4 && containsDigit(token)) {
                identifiers.add(token);
            }
        }
        return identifiers;
    }

    static double overlapRatio(List<String> queryTokens, List<String> candidateTokens) {
        if (queryTokens.isEmpty() || candidateTokens.isEmpty()) {
            return 0;
        }

        Set<String> candidateSet = new LinkedHashSet<>(candidateTokens);
        int matched = 0;
        for (String token : new LinkedHashSet<>(queryTokens)) {
            if (candidateSet.contains(token)) {
                matched++;
            }
        }
        return matched / (double) new LinkedHashSet<>(queryTokens).size();
    }

    static boolean containsQueryLiteral(String query, String candidate) {
        if (query == null || candidate == null) {
            return false;
        }
        String normalizedQuery = query.trim().toLowerCase(Locale.ROOT);
        String normalizedCandidate = candidate.toLowerCase(Locale.ROOT);
        return !normalizedQuery.isEmpty() && normalizedCandidate.contains(normalizedQuery);
    }

    private static void flushLatinBuffer(List<String> tokens, StringBuilder latinBuffer) {
        if (latinBuffer.length() == 0) {
            return;
        }
        tokens.add(latinBuffer.toString());
        latinBuffer.setLength(0);
    }

    private static boolean containsDigit(String token) {
        for (int index = 0; index < token.length(); index++) {
            if (Character.isDigit(token.charAt(index))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCjk(char current) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(current);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS;
    }
}
