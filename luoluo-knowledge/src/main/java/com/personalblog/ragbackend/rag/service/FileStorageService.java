package com.personalblog.ragbackend.rag.service;

import com.personalblog.ragbackend.rag.dto.StoredFileDTO;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

public interface FileStorageService {
    StoredFileDTO upload(String bucketName, MultipartFile file);

    StoredFileDTO upload(String bucketName, InputStream content, long size, String originalFilename, String contentType);

    StoredFileDTO upload(String bucketName, byte[] content, String originalFilename, String contentType);

    StoredFileDTO reliableUpload(String bucketName, InputStream content, long size, String originalFilename, String contentType);

    InputStream openStream(String url);

    void deleteByUrl(String url);
}
