package com.rca.agent.analyzer.log;

import java.time.Instant;
import java.util.Map;

/**
 * Represents a single parsed log entry.
 *
 * @param timestamp when the log event occurred
 * @param level     log severity level (e.g., ERROR, WARN, INFO, DEBUG)
 * @param message   the log message content
 * @param source    the originating class/logger name (may be empty)
 * @param metadata  additional key-value pairs extracted from the log line
 */
public record LogEntry(
        Instant timestamp,
        String level,
        String message,
        String source,
        Map<String, String> metadata
) {}
