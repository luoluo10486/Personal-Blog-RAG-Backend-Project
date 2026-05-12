package com.personalblog.ragbackend.rag.controller.request;

import java.util.List;

public class IntentNodeBatchRequest {
    private List<String> ids;

    public List<String> getIds() {
        return ids;
    }

    public void setIds(List<String> ids) {
        this.ids = ids;
    }
}
