package com.personalblog.ragbackend.knowledge.service.vector;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KnowledgeVectorSpaceId {

    private String logicalName;

    private String namespace;

    public String logicalName() {
        return logicalName;
    }

    public String namespace() {
        return namespace;
    }
}
