package com.personalblog.ragbackend.rag.core.retrieve.channel;

public interface SearchChannel {
    String getName();

    int getPriority();

    boolean isEnabled(SearchContext context);

    SearchChannelResult search(SearchContext context);

    SearchChannelType getType();
}
