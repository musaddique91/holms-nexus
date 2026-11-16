package com.maverickignite.holmes.ingestion;

import java.time.Instant;

/** A single error log line, normalised from whatever shape the source produced. */
public record ErrorEvent(
    String service,
    String environment,
    String level,
    String message,
    String stackTrace,
    String logger,
    String host,
    Instant timestamp,
    String source) {}
