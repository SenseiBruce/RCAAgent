package com.rca.agent.analyzer.log;

import com.rca.agent.config.RcaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Service for analyzing log content from files or raw text.
 * <p>
 * Supports multiple log formats through pluggable {@link LogParser} implementations.
 * Provides filtering, summarization, and format detection capabilities.
 */
@Service
public class LogAnalyzerService {

    private static final Logger log = LoggerFactory.getLogger(LogAnalyzerService.class);
    private static final Set<String> ERROR_LEVELS = Set.of("ERROR", "FATAL", "CRITICAL", "WARN", "WARNING");

    private final List<LogParser> parsers;
    private final RcaProperties properties;

    public LogAnalyzerService(List<LogParser> parsers, RcaProperties properties) {
        this.parsers = parsers;
        this.properties = properties;
    }

    /**
     * Reads and analyzes a log file from disk.
     *
     * @param filePath absolute path to the log file
     * @return list of parsed log entries
     * @throws IOException              if the file cannot be read
     * @throws IllegalArgumentException if the file exceeds the configured max size
     */
    public List<LogEntry> analyzeFromFile(String filePath) throws IOException {
        Path path = Path.of(filePath);
        long sizeMb = Files.size(path) / (1024 * 1024);
        if (sizeMb > properties.getLog().getMaxFileSizeMb()) {
            throw new IllegalArgumentException("Log file exceeds max size: " + sizeMb + "MB");
        }
        String content = Files.readString(path);
        return analyze(content);
    }

    /**
     * Parses raw log content using the most appropriate parser.
     *
     * @param content raw log text (multi-line)
     * @return list of parsed log entries
     */
    public List<LogEntry> analyze(String content) {
        List<LogEntry> entries = findParser(content).parse(content);
        log.info("Parsed {} log entries, {} errors/warnings",
                entries.size(), entries.stream().filter(this::isError).count());
        return entries;
    }

    /**
     * Filters entries to only include errors and warnings.
     *
     * @param entries all log entries
     * @return entries with level ERROR, FATAL, CRITICAL, WARN, or WARNING
     */
    public List<LogEntry> filterErrors(List<LogEntry> entries) {
        return entries.stream().filter(this::isError).toList();
    }

    /**
     * Creates a text summary of log entries suitable for LLM context.
     * <p>
     * Prioritizes error/warning entries. If none exist, includes up to 50 entries.
     *
     * @param entries parsed log entries to summarize
     * @return formatted text summary with counts and error details
     */
    public String summarizeForLlm(List<LogEntry> entries) {
        List<LogEntry> errors = filterErrors(entries);
        long errorCount = errors.size();
        List<LogEntry> toDisplay = errors.isEmpty() ? entries.stream().limit(50).toList() : errors;

        StringBuilder sb = new StringBuilder("LOG ANALYSIS:\n");
        sb.append("Total entries: ").append(entries.size()).append("\n");
        sb.append("Errors/Warnings: ").append(errorCount).append("\n\n");
        sb.append("ERROR ENTRIES:\n");
        toDisplay.stream().limit(100).forEach(e ->
                sb.append("[").append(e.timestamp()).append("] ")
                        .append(e.level()).append(" - ").append(e.message()).append("\n"));
        return sb.toString();
    }

    private LogParser findParser(String content) {
        return parsers.stream()
                .filter(p -> !(p instanceof PlainTextLogParser))
                .filter(p -> p.canParse(content))
                .findFirst()
                .orElseGet(() -> parsers.stream()
                        .filter(p -> p instanceof PlainTextLogParser)
                        .findFirst()
                        .orElseThrow());
    }

    private boolean isError(LogEntry entry) {
        return ERROR_LEVELS.contains(entry.level().toUpperCase());
    }
}
