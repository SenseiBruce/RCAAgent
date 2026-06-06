package com.rca.agent.analyzer.log;

import com.rca.agent.config.RcaProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LogAnalyzerServiceTest {

    private LogAnalyzerService service;
    private RcaProperties properties;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        properties = new RcaProperties();
        properties.getLog().setMaxFileSizeMb(1);
        List<LogParser> parsers = List.of(new JsonLogParser(), new PlainTextLogParser());
        service = new LogAnalyzerService(parsers, properties);
    }

    @Test
    void analyze_plainText_parsesEntries() {
        String content = """
                2024-01-15T10:30:00Z ERROR first error
                2024-01-15T10:30:01Z INFO info msg
                """;

        List<LogEntry> entries = service.analyze(content);

        assertThat(entries).hasSize(2);
    }

    @Test
    void analyze_jsonContent_usesJsonParser() {
        String content = "{\"level\": \"ERROR\", \"message\": \"json error\", \"timestamp\": \"2024-01-15T10:30:00Z\"}";

        List<LogEntry> entries = service.analyze(content);

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).message()).isEqualTo("json error");
    }

    @Test
    void analyzeFromFile_validFile_parsesContent() throws IOException {
        Path logFile = tempDir.resolve("test.log");
        Files.writeString(logFile, "2024-01-15T10:30:00Z ERROR file error\n");

        List<LogEntry> entries = service.analyzeFromFile(logFile.toString());

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).message()).contains("file error");
    }

    @Test
    void analyzeFromFile_exceedsMaxSize_throwsException() throws IOException {
        Path logFile = tempDir.resolve("large.log");
        // Create file > 1MB (our test limit)
        byte[] data = new byte[2 * 1024 * 1024];
        Files.write(logFile, data);

        assertThatThrownBy(() -> service.analyzeFromFile(logFile.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds max size");
    }

    @Test
    void filterErrors_returnsOnlyErrors() {
        String content = """
                2024-01-15T10:30:00Z ERROR an error
                2024-01-15T10:30:01Z INFO info msg
                2024-01-15T10:30:02Z WARN a warning
                2024-01-15T10:30:03Z FATAL fatal issue
                2024-01-15T10:30:04Z CRITICAL critical issue
                2024-01-15T10:30:05Z DEBUG debug msg
                """;

        List<LogEntry> entries = service.analyze(content);
        List<LogEntry> errors = service.filterErrors(entries);

        assertThat(errors).hasSize(4);
        assertThat(errors.stream().map(LogEntry::level))
                .containsExactlyInAnyOrder("ERROR", "WARN", "FATAL", "CRITICAL");
    }

    @Test
    void summarizeForLlm_withErrors_includesErrorEntries() {
        String content = """
                2024-01-15T10:30:00Z ERROR database timeout
                2024-01-15T10:30:01Z INFO request started
                """;

        List<LogEntry> entries = service.analyze(content);
        String summary = service.summarizeForLlm(entries);

        assertThat(summary).contains("LOG ANALYSIS:");
        assertThat(summary).contains("Total entries: 2");
        assertThat(summary).contains("Errors/Warnings: 1");
        assertThat(summary).contains("database timeout");
    }

    @Test
    void summarizeForLlm_noErrors_includesAllEntries() {
        // Use LogEntry directly to avoid parser ambiguity
        List<LogEntry> entries = List.of(
                new LogEntry(Instant.parse("2024-01-15T10:30:00Z"), "INFO", "first info", "", java.util.Map.of()),
                new LogEntry(Instant.parse("2024-01-15T10:30:01Z"), "INFO", "second info", "", java.util.Map.of())
        );
        String summary = service.summarizeForLlm(entries);

        assertThat(summary).contains("Errors/Warnings: 0");
        assertThat(summary).contains("first info");
        assertThat(summary).contains("second info");
    }

    @Test
    void analyze_fallsBackToPlainText_whenNoJsonParser() {
        // When content isn't JSON, it should fall back to PlainTextLogParser
        String content = "just plain text";
        List<LogEntry> entries = service.analyze(content);
        assertThat(entries).hasSize(1);
    }
}
