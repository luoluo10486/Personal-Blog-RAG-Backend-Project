package com.personalblog.ragbackend.ingestion.util;

import org.apache.tika.Tika;

public final class MimeTypeDetector {

    private static final Tika TIKA = new Tika();

    private MimeTypeDetector() {
    }

    public static String detect(byte[] bytes, String fileName) {
        if (bytes == null || bytes.length == 0) {
            return detectByName(fileName);
        }
        return TIKA.detect(bytes, fileName);
    }

    public static String detectByName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "application/octet-stream";
        }
        return TIKA.detect(fileName);
    }
}
