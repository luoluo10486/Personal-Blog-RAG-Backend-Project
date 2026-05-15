package com.personalblog.ragbackend.rag.core.retrieve.channel;

import com.personalblog.ragbackend.infra.convention.RetrievedChunk;
import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class SearchChannelResult {
    private SearchChannelType channelType;
    private String channelName;
    private List<RetrievedChunk> chunks;
    private long latencyMs;
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
}
