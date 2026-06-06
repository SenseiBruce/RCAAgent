package com.rca.agent.analyzer.log;

import java.util.List;

/**
 * Strategy interface for parsing log content into structured {@link LogEntry} objects.
 * <p>
 * Implementations handle specific log formats (JSON, plaintext, CloudWatch, etc.).
 * The {@link LogAnalyzerService} selects the appropriate parser based on content detection.
 */
public interface LogParser {

    /**
     * Determines whether this parser can handle the given log content.
     *
     * @param content raw log content to evaluate
     * @return {@code true} if this parser supports the format
     */
    boolean canParse(String content);

    /**
     * Parses raw log content into a list of structured log entries.
     *
     * @param content raw log content (may contain multiple lines)
     * @return list of parsed {@link LogEntry} objects
     */
    List<LogEntry> parse(String content);
}
