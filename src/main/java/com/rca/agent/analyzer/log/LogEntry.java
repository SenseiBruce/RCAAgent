package com.rca.agent.analyzer.log;

import java.time.Instant;
import java.util.Map;

public record LogEntry(
        Instant timestamp,
        String level,
        String message,
        String source,
        Map<String, String> metadata
) {}
