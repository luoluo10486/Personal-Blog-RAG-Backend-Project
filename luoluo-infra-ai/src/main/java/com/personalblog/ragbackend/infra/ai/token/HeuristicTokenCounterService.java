package com.personalblog.ragbackend.infra.ai.token;

import org.springframework.stereotype.Service;

@Service
public class HeuristicTokenCounterService implements TokenCounterService {

    @Override
    public Integer countTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }

        int asciiCount = 0;
        int cjkCount = 0;
        int otherCount = 0;
        for (int index = 0; index < text.length(); index++) {
            char ch = text.charAt(index);
            if (Character.isWhitespace(ch)) {
                continue;
            }
            if (ch <= 0x7F) {
                asciiCount++;
            } else if (isCjk(ch)) {
                cjkCount++;
            } else {
                otherCount++;
            }
        }

        int asciiTokens = (asciiCount + 3) / 4;
        int otherTokens = (otherCount + 1) / 2;
        return Math.max(asciiTokens + cjkCount + otherTokens, 1);
    }

    private boolean isCjk(char ch) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(ch);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_C
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_D
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_E
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_F
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS_SUPPLEMENT
                || block == Character.UnicodeBlock.CJK_RADICALS_SUPPLEMENT
                || block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || block == Character.UnicodeBlock.HIRAGANA
                || block == Character.UnicodeBlock.KATAKANA
                || block == Character.UnicodeBlock.KATAKANA_PHONETIC_EXTENSIONS
                || block == Character.UnicodeBlock.HANGUL_SYLLABLES
                || block == Character.UnicodeBlock.HANGUL_JAMO
                || block == Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO;
    }
}
