package com.personalblog.ragbackend.knowledge.dto.admin;

public class PageRequest {
    private long current = 1;
    private long size = 10;

    public long getCurrent() {
        return current;
    }

    public void setCurrent(long current) {
        this.current = current <= 0 ? 1 : current;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size <= 0 ? 10 : size;
    }
}
