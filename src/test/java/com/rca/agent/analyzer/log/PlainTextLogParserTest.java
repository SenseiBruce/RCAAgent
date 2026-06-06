package com.rca.agent.analyzer.log;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlainTextLogParserTest {

    private PlainTextLogParser parser;

    @BeforeEach
    void setUp() {
        parser = new PlainTextLogParser();
    }

    @Test
    void canParse_alwaysReturnsTrue() {
        assertThat(parser.canParse("anything")).isTrue();
        assertThat(parser.canParse("")).isTrue();
        assertThat(parser.canParse("{json}")).isTrue();
    }

    @Test
    void parse_standardLogFormat_parsesCorrectly() {
        String content = "2024-01-15T10:30:00Z ERROR - NullPointerException at UserService.java:42";
        List<LogEntry> entries = parser.parse(content);

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).level()).isEqualTo("ERROR");
        assertThat(entries.get(0).timestamp()).isEqualTo(Instant.parse("2024-01-15T10:30:00Z"));
        assertThat(entries.get(0).message()).contains("NullPointerException");
    }

    @Test
    void parse_withoutZ_appendsZ() {
        String content = "2024-01-15T10:30:00 ERROR something happened";
        List<LogEntry> entries = parser.parse(content);

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).timestamp()).isEqualTo(Instant.parse("2024-01-15T10:30:00Z"));
    }

    @Test
    void parse_withMilliseconds_parsesCorrectly() {
        String content = "2024-01-15T10:30:00.123Z ERROR with millis";
        List<LogEntry> entries = parser.parse(content);

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).level()).isEqualTo("ERROR");
    }

    @Test
    void parse_warnLevel_parsesCorrectly() {
        String content = "2024-01-15T10:30:00Z WARN low disk space";
        List<LogEntry> entries = parser.parse(content);

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).level()).isEqualTo("WARN");
    }

    @Test
    void parse_warningLevel_parsesCorrectly() {
        String content = "2024-01-15T10:30:00Z WARNING deprecated API usage";
        List<LogEntry> entries = parser.parse(content);

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).level()).isIn("WARN", "WARNING");
    }

    @Test
    void parse_debugLevel_parsesCorrectly() {
        String content = "2024-01-15T10:30:00Z DEBUG entering method";
        List<LogEntry> entries = parser.parse(content);

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).level()).isEqualTo("DEBUG");
    }

    @Test
    void parse_fatalLevel_parsesCorrectly() {
        String content = "2024-01-15T10:30:00Z FATAL system crash";
        List<LogEntry> entries = parser.parse(content);

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).level()).isEqualTo("FATAL");
    }

    @Test
    void parse_criticalLevel_parsesCorrectly() {
        String content = "2024-01-15T10:30:00Z CRITICAL out of memory";
        List<LogEntry> entries = parser.parse(content);

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).level()).isEqualTo("CRITICAL");
    }

    @Test
    void parse_bracketedLevel_parsesCorrectly() {
        String content = "2024-01-15T10:30:00Z [ERROR] something broke";
        List<LogEntry> entries = parser.parse(content);

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).level()).isEqualTo("ERROR");
    }

    @Test
    void parse_noMatchingPattern_returnsInfoLevel() {
        String content = "just some random text without a timestamp";
        List<LogEntry> entries = parser.parse(content);

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).level()).isEqualTo("INFO");
        assertThat(entries.get(0).message()).isEqualTo("just some random text without a timestamp");
    }

    @Test
    void parse_blankLines_skipped() {
        String content = """
                2024-01-15T10:30:00Z ERROR first
                
                2024-01-15T10:30:01Z INFO second
                """;

        List<LogEntry> entries = parser.parse(content);
        assertThat(entries).hasSize(2);
    }

    @Test
    void parse_multipleEntries_parsesAll() {
        String content = """
                2024-01-15T10:30:00Z ERROR first error
                2024-01-15T10:30:01Z WARN a warning
                2024-01-15T10:30:02Z INFO informational
                """;

        List<LogEntry> entries = parser.parse(content);
        assertThat(entries).hasSize(3);
        assertThat(entries.get(0).level()).isEqualTo("ERROR");
        assertThat(entries.get(1).level()).isEqualTo("WARN");
        assertThat(entries.get(2).level()).isEqualTo("INFO");
    }

    @Test
    void parse_spaceTimeSeparator_parsesCorrectly() {
        String content = "2024-01-15 10:30:00Z INFO space separated";
        List<LogEntry> entries = parser.parse(content);

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).level()).isEqualTo("INFO");
    }

    @Test
    void parse_invalidTimestamp_usesCurrentTime() {
        // This matches the regex but the timestamp can't be parsed as Instant
        String content = "2024-13-45T99:99:99Z ERROR bad time";
        Instant before = Instant.now();
        List<LogEntry> entries = parser.parse(content);

        assertThat(entries.get(0).timestamp()).isAfterOrEqualTo(before);
    }

    @Test
    void parse_sourceIsAlwaysEmpty() {
        String content = "2024-01-15T10:30:00Z ERROR test";
        List<LogEntry> entries = parser.parse(content);
        assertThat(entries.get(0).source()).isEmpty();
    }

    @Test
    void parse_metadataIsAlwaysEmpty() {
        String content = "2024-01-15T10:30:00Z ERROR test";
        List<LogEntry> entries = parser.parse(content);
        assertThat(entries.get(0).metadata()).isEmpty();
    }

    @Test
    void parse_caseInsensitiveLevel() {
        String content = "2024-01-15T10:30:00Z error lowercase level";
        List<LogEntry> entries = parser.parse(content);

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).level()).isEqualTo("ERROR");
    }
}
