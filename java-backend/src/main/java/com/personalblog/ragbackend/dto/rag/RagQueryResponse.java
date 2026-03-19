package com.personalblog.ragbackend.dto.rag;

import java.util.List;

public record RagQueryResponse(
        String answer,
        List<RagReferenceResponse> references
) {
}
