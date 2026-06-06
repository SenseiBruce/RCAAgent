package com.rca.agent.analyzer.log;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JsonLogParserTest {

    private JsonLogParser parser;

    @BeforeEach
    void setUp() {
        parser = new JsonLogParser();
    }

    @Test
    void canParse_jsonObject_returnsTrue() {
        assertThat(parser.canParse("{\"level\": \"ERROR\"}")).isTrue();
    }

    @Test
    void canParse_jsonArray_returnsTrue() {
        assertThat(parser.canParse("[{\"level\": \"ERROR\"}]")).isTrue();
    }

    @Test
    void canParse_plainText_returnsFalse() {
        assertThat(parser.canParse("2024-01-01 ERROR something broke")).isFalse();
    }

    @Test
    void canParse_withWhitespace_returnsTrue() {
        assertThat(parser.canParse("  {\"level\": \"ERROR\"}")).isTrue();
    }

    @Test
    void parse_validJsonLines_parsesCorrectly() {
        String content = """
                {"timestamp": "2024-01-15T10:30:00Z", "level": "ERROR", "message": "NPE occurred", "logger": "UserService"}
                {"timestamp": "2024-01-15T10:30:01Z", "level": "INFO", "message": "Request received", "source": "Controller"}
                """;

        List<LogEntry> entries = parser.parse(content);

        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).level()).isEqualTo("ERROR");
        assertThat(entries.get(0).message()).isEqualTo("NPE occurred");
        assertThat(entries.get(0).source()).isEqualTo("UserService");
        assertThat(entries.get(0).timestamp()).isEqualTo(Instant.parse("2024-01-15T10:30:00Z"));
        assertThat(entries.get(1).source()).isEqualTo("Controller");
    }

    @Test
    void parse_alternateFieldNames_parsesCorrectly() {
        String content = """
                {"@timestamp": "2024-01-15T10:30:00Z", "severity": "WARN", "msg": "Slow query", "class": "DBPool"}
                """;

        List<LogEntry> entries = parser.parse(content);

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).level()).isEqualTo("WARN");
        assertThat(entries.get(0).message()).isEqualTo("Slow query");
        assertThat(entries.get(0).source()).isEqualTo("DBPool");
    }

    @Test
    void parse_timeField_parsesCorrectly() {
        String content = "{\"time\": \"2024-03-01T08:00:00Z\", \"level\": \"DEBUG\", \"message\": \"test\"}";
        List<LogEntry> entries = parser.parse(content);
        assertThat(entries.get(0).timestamp()).isEqualTo(Instant.parse("2024-03-01T08:00:00Z"));
    }

    @Test
    void parse_datetimeField_parsesCorrectly() {
        String content = "{\"datetime\": \"2024-03-01T08:00:00Z\", \"level\": \"INFO\", \"message\": \"test\"}";
        List<LogEntry> entries = parser.parse(content);
        assertThat(entries.get(0).timestamp()).isEqualTo(Instant.parse("2024-03-01T08:00:00Z"));
    }

    @Test
    void parse_noTimestamp_usesCurrentTime() {
        String content = "{\"level\": \"ERROR\", \"message\": \"no time\"}";
        Instant before = Instant.now();
        List<LogEntry> entries = parser.parse(content);
        assertThat(entries.get(0).timestamp()).isAfterOrEqualTo(before);
    }

    @Test
    void parse_invalidTimestamp_usesCurrentTime() {
        String content = "{\"timestamp\": \"not-a-date\", \"level\": \"ERROR\", \"message\": \"bad time\"}";
        Instant before = Instant.now();
        List<LogEntry> entries = parser.parse(content);
        assertThat(entries.get(0).timestamp()).isAfterOrEqualTo(before);
    }

    @Test
    void parse_missingFields_returnsEmptyStrings() {
        String content = "{\"foo\": \"bar\"}";
        List<LogEntry> entries = parser.parse(content);

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).level()).isEmpty();
        assertThat(entries.get(0).message()).isEmpty();
        assertThat(entries.get(0).source()).isEmpty();
    }

    @Test
    void parse_emptyLines_skipped() {
        String content = """
                {"level": "ERROR", "message": "first"}
                
                {"level": "INFO", "message": "second"}
                """;

        List<LogEntry> entries = parser.parse(content);
        assertThat(entries).hasSize(2);
    }

    @Test
    void parse_invalidJson_skipped() {
        String content = """
                {"level": "ERROR", "message": "valid"}
                {invalid json here
                {"level": "INFO", "message": "also valid"}
                """;

        List<LogEntry> entries = parser.parse(content);
        assertThat(entries).hasSize(2);
    }

    @Test
    void parse_nonJsonStartingLine_skipped() {
        String content = """
                some plain text line
                {"level": "ERROR", "message": "valid"}
                """;

        List<LogEntry> entries = parser.parse(content);
        assertThat(entries).hasSize(1);
    }

    @Test
    void parse_metadata_extracted() {
        String content = "{\"level\": \"ERROR\", \"message\": \"test\", \"traceId\": \"abc123\", \"spanId\": \"def456\"}";
        List<LogEntry> entries = parser.parse(content);

        assertThat(entries.get(0).metadata()).containsEntry("traceId", "abc123");
        assertThat(entries.get(0).metadata()).containsEntry("spanId", "def456");
    }

    @Test
    void parse_errorField_usedAsMessage() {
        String content = "{\"level\": \"ERROR\", \"error\": \"something failed\"}";
        List<LogEntry> entries = parser.parse(content);
        assertThat(entries.get(0).message()).isEqualTo("something failed");
    }

    @Test
    void parse_logLevelField_usedAsLevel() {
        String content = "{\"log_level\": \"CRITICAL\", \"message\": \"test\"}";
        List<LogEntry> entries = parser.parse(content);
        assertThat(entries.get(0).level()).isEqualTo("CRITICAL");
    }
}
