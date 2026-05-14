package com.personalblog.ragbackend.ingestion.domain.context;

import com.personalblog.ragbackend.ingestion.domain.enums.SourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentSource {
    private SourceType type;
    private String location;
    private String fileName;
    private Map<String, String> credentials;
}
