package com.personalblog.ragbackend.knowledge.schedule;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeDocumentEntity;
import com.personalblog.ragbackend.knowledge.mapper.KnowledgeDocumentMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Date;

@Component
public class DocumentStatusHelper {
    private static final String SYSTEM_USER = "system";

    private final KnowledgeDocumentMapper documentMapper;

    public DocumentStatusHelper(KnowledgeDocumentMapper documentMapper) {
        this.documentMapper = documentMapper;
    }

    public boolean tryMarkRunning(String docId) {
        if (!StringUtils.hasText(docId)) {
            return false;
        }
        return documentMapper.update(
                Wrappers.lambdaUpdate(KnowledgeDocumentEntity.class)
                        .set(KnowledgeDocumentEntity::getStatus, "running")
                        .set(KnowledgeDocumentEntity::getUpdatedBy, null)
                        .eq(KnowledgeDocumentEntity::getId, Long.valueOf(docId))
                        .eq(KnowledgeDocumentEntity::getDeleted, 0)
                        .eq(KnowledgeDocumentEntity::getEnabled, 1)
                        .ne(KnowledgeDocumentEntity::getStatus, "running")
        ) > 0;
    }

    public void markFailedIfRunning(String docId) {
        if (!StringUtils.hasText(docId)) {
            return;
        }
        documentMapper.update(
                Wrappers.lambdaUpdate(KnowledgeDocumentEntity.class)
                        .set(KnowledgeDocumentEntity::getStatus, "failed")
                        .set(KnowledgeDocumentEntity::getUpdatedBy, null)
                        .eq(KnowledgeDocumentEntity::getId, Long.valueOf(docId))
                        .eq(KnowledgeDocumentEntity::getStatus, "running")
        );
    }

    public void applyRefreshedFileMetadata(String docId, String fileName, String fileUrl, String fileType, Long fileSize) {
        if (!StringUtils.hasText(docId)) {
            return;
        }
        KnowledgeDocumentEntity update = new KnowledgeDocumentEntity();
        update.setId(Long.valueOf(docId));
        update.setDocName(fileName);
        update.setFileUrl(fileUrl);
        update.setFileType(fileType);
        update.setFileSize(fileSize);
        int updated = documentMapper.updateById(update);
        if (updated == 0) {
            throw new IllegalArgumentException("document not found");
        }
    }

    public int recoverStuckRunning(long timeoutMinutes) {
        long safeTimeout = Math.max(timeoutMinutes, 10);
        Date threshold = new Date(System.currentTimeMillis() - safeTimeout * 60 * 1000);
        return documentMapper.update(
                Wrappers.lambdaUpdate(KnowledgeDocumentEntity.class)
                        .set(KnowledgeDocumentEntity::getStatus, "failed")
                        .set(KnowledgeDocumentEntity::getUpdatedBy, null)
                        .eq(KnowledgeDocumentEntity::getStatus, "running")
                        .lt(KnowledgeDocumentEntity::getUpdatedAt, threshold.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime())
        );
    }
}
