package com.personalblog.ragbackend.knowledge.service.document;

import com.personalblog.ragbackend.knowledge.config.RustfsProperties;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

@Service
public class KnowledgeFileStorageService {
    private static final Tika TIKA = new Tika();
    private static final String DEFAULT_BUCKET_PREFIX = "knowledge";

    private final S3Client s3Client;
    private final RustfsProperties rustfsProperties;

    public KnowledgeFileStorageService(S3Client s3Client,
                                       RustfsProperties rustfsProperties) {
        this.s3Client = s3Client;
        this.rustfsProperties = rustfsProperties;
    }

    public String store(MultipartFile file, String collectionName, String docName) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        try {
            String bucketName = resolveBucketName(collectionName);
            ensureBucketExists(bucketName);
            String key = buildObjectKey(docName, file.getOriginalFilename());
            String contentType = resolveContentType(file);
            try (InputStream inputStream = file.getInputStream()) {
                s3Client.putObject(builder -> builder
                                .bucket(bucketName)
                                .key(key)
                                .contentType(contentType),
                        RequestBody.fromInputStream(inputStream, file.getSize()));
            }
            return toS3Url(bucketName, key);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to store knowledge file", exception);
        }
    }

    public MultipartFile restore(String fileUrl, String originalFilename, String contentType) {
        if (!StringUtils.hasText(fileUrl)) {
            return null;
        }
        if (fileUrl.startsWith("s3://")) {
            return new S3RestoredMultipartFile(parseS3Location(fileUrl), originalFilename, contentType);
        }
        return new LocalRestoredMultipartFile(Path.of(fileUrl), originalFilename, contentType);
    }

    public void deleteByUrl(String fileUrl) {
        if (!StringUtils.hasText(fileUrl)) {
            return;
        }
        if (fileUrl.startsWith("s3://")) {
            S3Location location = parseS3Location(fileUrl);
            s3Client.deleteObject(builder -> builder.bucket(location.bucket()).key(location.key()));
            return;
        }
        try {
            Files.deleteIfExists(Path.of(fileUrl));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to delete knowledge file", exception);
        }
    }

    public InputStream openStream(String fileUrl) {
        if (!StringUtils.hasText(fileUrl)) {
            return InputStream.nullInputStream();
        }
        if (fileUrl.startsWith("s3://")) {
            S3Location location = parseS3Location(fileUrl);
            return s3Client.getObject(builder -> builder.bucket(location.bucket()).key(location.key()));
        }
        try {
            return Files.newInputStream(Path.of(fileUrl));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to open knowledge file", exception);
        }
    }

    public String resolveBucketName(String collectionName) {
        String prefix = sanitizeBucketSegment(DEFAULT_BUCKET_PREFIX);
        String source = StringUtils.hasText(collectionName)
                ? collectionName
                : "rag_default_store";
        String segment = sanitizeBucketSegment(source);
        String candidate = prefix + "-" + segment;
        if (candidate.length() <= 63) {
            return candidate;
        }
        String hash = Integer.toHexString(candidate.hashCode());
        int keepLength = Math.max(1, 63 - hash.length() - 1);
        String trimmed = candidate.substring(0, keepLength).replaceAll("[-.]+$", "");
        return trimmed + "-" + hash;
    }

    public void ensureBucketExists(String bucketName) {
        validateBucketName(bucketName);
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
        } catch (S3Exception exception) {
            if (exception.statusCode() != 404) {
                throw exception;
            }
            try {
                s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
            } catch (S3Exception createException) {
                if (createException.statusCode() != 409) {
                    throw createException;
                }
            }
        }
    }

    private String resolveContentType(MultipartFile file) {
        String contentType = file.getContentType();
        if (StringUtils.hasText(contentType)) {
            return contentType;
        }
        try (InputStream inputStream = file.getInputStream()) {
            return TIKA.detect(inputStream, file.getOriginalFilename());
        } catch (IOException exception) {
            return file.getOriginalFilename() == null ? "application/octet-stream" : TIKA.detect(file.getOriginalFilename());
        }
    }

    private String buildObjectKey(String docName, String originalFilename) {
        String safeName = sanitizeFileName(StringUtils.hasText(docName) ? docName : originalFilename);
        String suffix = suffixOf(originalFilename);
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return "documents/" + System.currentTimeMillis() + "_" + safeName + "_" + uuid.substring(0, 12) + suffix;
    }

    private String toS3Url(String bucket, String key) {
        return "s3://" + bucket + "/" + key;
    }

    private S3Location parseS3Location(String fileUrl) {
        String value = fileUrl.substring("s3://".length());
        int slashIndex = value.indexOf('/');
        if (slashIndex <= 0 || slashIndex == value.length() - 1) {
            throw new IllegalArgumentException("Invalid s3 url: " + fileUrl);
        }
        return new S3Location(value.substring(0, slashIndex), value.substring(slashIndex + 1));
    }

    private String sanitizeBucketSegment(String value) {
        String sanitized = StringUtils.hasText(value)
                ? value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]+", "-")
                : "default";
        sanitized = sanitized.replaceAll("-+", "-").replaceAll("^-|-$", "");
        return StringUtils.hasText(sanitized) ? sanitized : "default";
    }

    private String sanitizeFileName(String value) {
        String sanitized = StringUtils.hasText(value)
                ? value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "_")
                : "uploaded-document";
        sanitized = sanitized.replaceAll("_+", "_").replaceAll("^_|_$", "");
        return StringUtils.hasText(sanitized) ? sanitized : "uploaded-document";
    }

    private String suffixOf(String filename) {
        if (!StringUtils.hasText(filename) || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.'));
    }

    private void validateBucketName(String bucketName) {
        if (!StringUtils.hasText(bucketName)) {
            throw new IllegalArgumentException("bucketName 不能为空");
        }
    }

    private record S3Location(String bucket, String key) {
    }

    private final class S3RestoredMultipartFile implements MultipartFile {
        private final S3Location location;
        private final String originalFilename;
        private final String contentType;

        private S3RestoredMultipartFile(S3Location location, String originalFilename, String contentType) {
            this.location = location;
            this.originalFilename = StringUtils.hasText(originalFilename)
                    ? originalFilename
                    : location.key().substring(location.key().lastIndexOf('/') + 1);
            this.contentType = contentType;
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
            return getSize() <= 0;
        }

        @Override
        public long getSize() {
            try {
                return s3Client.headObject(HeadObjectRequest.builder()
                        .bucket(location.bucket())
                        .key(location.key())
                        .build()).contentLength();
            } catch (S3Exception exception) {
                return 0L;
            }
        }

        @Override
        public byte[] getBytes() {
            ResponseBytes<?> responseBytes = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(location.bucket())
                    .key(location.key())
                    .build());
            return responseBytes.asByteArray();
        }

        @Override
        public InputStream getInputStream() {
            return s3Client.getObject(GetObjectRequest.builder()
                    .bucket(location.bucket())
                    .key(location.key())
                    .build());
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
            try (InputStream inputStream = getInputStream()) {
                Files.copy(inputStream, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static final class LocalRestoredMultipartFile implements MultipartFile {
        private final Path path;
        private final String originalFilename;
        private final String contentType;

        private LocalRestoredMultipartFile(Path path, String originalFilename, String contentType) {
            this.path = path;
            this.originalFilename = StringUtils.hasText(originalFilename)
                    ? originalFilename
                    : path.getFileName().toString();
            this.contentType = contentType;
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
            return !Files.exists(path);
        }

        @Override
        public long getSize() {
            try {
                return Files.size(path);
            } catch (IOException exception) {
                return 0L;
            }
        }

        @Override
        public byte[] getBytes() throws IOException {
            return Files.readAllBytes(path);
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return Files.newInputStream(path);
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
            Files.copy(path, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
