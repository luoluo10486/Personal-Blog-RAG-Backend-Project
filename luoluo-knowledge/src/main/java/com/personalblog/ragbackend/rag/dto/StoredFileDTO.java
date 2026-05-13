package com.personalblog.ragbackend.rag.dto;

public class StoredFileDTO {
    private String url;
    private String originalFilename;
    private String detectedType;
    private Long size;

    public StoredFileDTO() {
    }

    public StoredFileDTO(String url, String originalFilename, String detectedType, Long size) {
        this.url = url;
        this.originalFilename = originalFilename;
        this.detectedType = detectedType;
        this.size = size;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public String getDetectedType() {
        return detectedType;
    }

    public void setDetectedType(String detectedType) {
        this.detectedType = detectedType;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }
}
