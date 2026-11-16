package com.maverickignite.mcp.llm;

/** Published after a provider is created, edited or removed, so cached clients (MVXHolmes) rebuild. */
public record ProvidersChangedEvent() {}
