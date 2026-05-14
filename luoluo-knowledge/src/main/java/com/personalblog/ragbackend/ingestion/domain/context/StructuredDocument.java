package com.personalblog.ragbackend.ingestion.domain.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StructuredDocument {
    private String text;
    private List<StructuredSection> sections;
    private List<StructuredTable> tables;
    private Map<String, Object> metadata;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public List<StructuredSection> getSections() {
        return sections;
    }

    public void setSections(List<StructuredSection> sections) {
        this.sections = sections;
    }

    public List<StructuredTable> getTables() {
        return tables;
    }

    public void setTables(List<StructuredTable> tables) {
        this.tables = tables;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StructuredSection {
        private String title;
        private Integer level;
        private String content;
        private Integer startOffset;
        private Integer endOffset;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public Integer getLevel() {
            return level;
        }

        public void setLevel(Integer level) {
            this.level = level;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public Integer getStartOffset() {
            return startOffset;
        }

        public void setStartOffset(Integer startOffset) {
            this.startOffset = startOffset;
        }

        public Integer getEndOffset() {
            return endOffset;
        }

        public void setEndOffset(Integer endOffset) {
            this.endOffset = endOffset;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StructuredTable {
        private String title;
        private List<List<String>> rows;
        private Integer startOffset;
        private Integer endOffset;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public List<List<String>> getRows() {
            return rows;
        }

        public void setRows(List<List<String>> rows) {
            this.rows = rows;
        }

        public Integer getStartOffset() {
            return startOffset;
        }

        public void setStartOffset(Integer startOffset) {
            this.startOffset = startOffset;
        }

        public Integer getEndOffset() {
            return endOffset;
        }

        public void setEndOffset(Integer endOffset) {
            this.endOffset = endOffset;
        }
    }
}
