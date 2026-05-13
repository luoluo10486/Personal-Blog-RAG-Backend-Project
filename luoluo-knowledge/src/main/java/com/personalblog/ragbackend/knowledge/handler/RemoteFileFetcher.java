package com.personalblog.ragbackend.knowledge.handler;

import com.personalblog.ragbackend.knowledge.service.document.KnowledgeFileStorageService;
import com.personalblog.ragbackend.rag.dto.StoredFileDTO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class RemoteFileFetcher {
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final KnowledgeFileStorageService fileStorageService;

    public RemoteFileFetcher(KnowledgeFileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    public StoredFileDTO fetchAndStore(String bucketName, String url) {
        if (!StringUtils.hasText(url)) {
            throw new IllegalArgumentException("url must not be blank");
        }
        try {
            HttpResponse<byte[]> response = httpClient.send(
                    HttpRequest.newBuilder(URI.create(url.trim())).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray()
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalArgumentException("download remote file failed, status=" + response.statusCode());
            }
            String fileName = resolveFileName(url, response);
            String contentType = response.headers().firstValue("content-type").orElse("application/octet-stream");
            MultipartFile file = new InMemoryMultipartFile(fileName, contentType, response.body());
            String storedUrl = fileStorageService.store(file, bucketName, fileName);
            return new StoredFileDTO(storedUrl, fileName, contentType, (long) response.body().length);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to fetch remote file", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to fetch remote file", exception);
        }
    }

    private String resolveFileName(String url, HttpResponse<byte[]> response) {
        String disposition = response.headers().firstValue("content-disposition").orElse(null);
        if (StringUtils.hasText(disposition)) {
            int index = disposition.indexOf("filename=");
            if (index >= 0) {
                String value = disposition.substring(index + "filename=".length()).replace("\"", "");
                if (StringUtils.hasText(value)) {
                    return value.trim();
                }
            }
        }
        try {
            Path path = Path.of(URI.create(url).getPath());
            Path fileName = path.getFileName();
            if (fileName != null && StringUtils.hasText(fileName.toString())) {
                return fileName.toString();
            }
        } catch (Exception ignored) {
        }
        return "remote-file";
    }

    private static final class InMemoryMultipartFile implements MultipartFile {
        private final String originalFilename;
        private final String contentType;
        private final byte[] content;

        private InMemoryMultipartFile(String originalFilename, String contentType, byte[] content) {
            this.originalFilename = originalFilename;
            this.contentType = contentType;
            this.content = content == null ? new byte[0] : content.clone();
        }

        @Override
        public String getName() {
            return originalFilename;
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return content.length == 0;
        }

        @Override
        public long getSize() {
            return content.length;
        }

        @Override
        public byte[] getBytes() {
            return content.clone();
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException {
            Files.write(dest.toPath(), content);
        }
    }
}
