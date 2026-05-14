package com.personalblog.ragbackend.rag.core.vector;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VectorSpaceId {

    private String logicalName;

    private String namespace;

    public String logicalName() {
        return logicalName;
    }

    public String namespace() {
        return namespace;
    }
}
