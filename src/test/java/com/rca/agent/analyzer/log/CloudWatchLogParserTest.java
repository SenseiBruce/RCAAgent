package com.rca.agent.analyzer.log;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CloudWatchLogParserTest {

    private CloudWatchLogParser parser;

    @BeforeEach
    void setUp() {
        parser = new CloudWatchLogParser();
    }

    // --- canParse tests ---

    @Test
    void canParse_cloudWatchApiResponse_returnsTrue() {
        String content = """
                {"events": [{"timestamp": 1705312200000, "message": "ERROR something failed"}], "nextForwardToken": "abc"}
                """;
        assertThat(parser.canParse(content)).isTrue();
    }

    @Test
    void canParse_cloudWatchInsightsResult_returnsTrue() {
        String content = """
                {"results": [[{"field": "@timestamp", "value": "2024-01-15T10:30:00Z"}, {"field": "@message", "value": "error"}]]}
                """;
        assertThat(parser.canParse(content)).isTrue();
    }

    @Test
    void canParse_cloudWatchEventsArray_returnsTrue() {
        String content = """
                [{"timestamp": 1705312200000, "message": "log line"}]
                """;
        assertThat(parser.canParse(content)).isTrue();
    }

    @Test
    void canParse_regularJson_returnsFalse() {
        String content = """
                {"level": "ERROR", "message": "not cloudwatch"}
                """;
        assertThat(parser.canParse(content)).isFalse();
    }

    @Test
    void canParse_plainText_returnsFalse() {
        assertThat(parser.canParse("2024-01-15 ERROR plain text log")).isFalse();
    }

    @Test
    void canParse_invalidJson_returnsFalse() {
        assertThat(parser.canParse("{invalid json")).isFalse();
    }

    @Test
    void canParse_emptyEventsArray_returnsFalse() {
        assertThat(parser.canParse("[]")).isFalse();
    }

    // --- CloudWatch API response parsing ---

    @Test
    void parse_apiResponse_extractsTimestampAndMessage() {
        String content = """
                {
                    "events": [
                        {"timestamp": 1705312200000, "message": "2024-01-15 ERROR NullPointerException at UserService.java:42"},
                        {"timestamp": 1705312260000, "message": "2024-01-15 INFO Request processed successfully"}
                    ],
                    "nextForwardToken": "f/123"
                }
                """;

        List<LogEntry> entries = parser.parse(content);

        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).timestamp()).isEqualTo(Instant.ofEpochMilli(1705312200000L));
        assertThat(entries.get(0).level()).isEqualTo("ERROR");
        assertThat(entries.get(0).message()).contains("NullPointerException");
        assertThat(entries.get(1).level()).isEqualTo("INFO");
    }

    @Test
    void parse_apiResponse_extractsLogStreamAndGroup() {
        String content = """
                {
                    "events": [
                        {"timestamp": 1705312200000, "message": "test", "logStreamName": "stream-1", "logGroupName": "/aws/lambda/my-function"}
                    ]
                }
                """;

        List<LogEntry> entries = parser.parse(content);

        assertThat(entries.get(0).source()).isEqualTo("stream-1");
        assertThat(entries.get(0).metadata()).containsEntry("logStream", "stream-1");
        assertThat(entries.get(0).metadata()).containsEntry("logGroup", "/aws/lambda/my-function");
    }

    @Test
    void parse_apiResponse_extractsIngestionTime() {
        String content = """
                {"events": [{"timestamp": 1705312200000, "message": "test", "ingestionTime": 1705312201000}]}
                """;

        List<LogEntry> entries = parser.parse(content);

        assertThat(entries.get(0).metadata()).containsKey("ingestionTime");
    }

    @Test
    void parse_apiResponse_emptyEvents_returnsEmpty() {
        String content = """
                {"events": [], "nextForwardToken": "f/123"}
                """;

        assertThat(parser.parse(content)).isEmpty();
    }

    // --- CloudWatch Events Array ---

    @Test
    void parse_eventsArray_parsesCorrectly() {
        String content = """
                [
                    {"timestamp": 1705312200000, "message": "WARN connection pool exhausted"},
                    {"timestamp": 1705312201000, "message": "ERROR timeout after 30s"}
                ]
                """;

        List<LogEntry> entries = parser.parse(content);

        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).level()).isEqualTo("WARN");
        assertThat(entries.get(1).level()).isEqualTo("ERROR");
        assertThat(entries.get(1).message()).contains("timeout");
    }

    @Test
    void parse_eventsArray_withIsoTimestamp() {
        String content = """
                [{"@timestamp": "2024-01-15T10:30:00Z", "message": "DEBUG processing request"}]
                """;

        List<LogEntry> entries = parser.parse(content);

        assertThat(entries.get(0).timestamp()).isEqualTo(Instant.parse("2024-01-15T10:30:00Z"));
        assertThat(entries.get(0).level()).isEqualTo("DEBUG");
    }

    // --- CloudWatch Insights results ---

    @Test
    void parse_insightsResult_parsesFieldValueStructure() {
        String content = """
                {
                    "results": [
                        [
                            {"field": "@timestamp", "value": "2024-01-15T10:30:00Z"},
                            {"field": "@message", "value": "ERROR Failed to connect to database"},
                            {"field": "@logStream", "value": "app/service/abc123"}
                        ],
                        [
                            {"field": "@timestamp", "value": "2024-01-15T10:30:05Z"},
                            {"field": "@message", "value": "INFO Retrying connection"},
                            {"field": "@logStream", "value": "app/service/abc123"}
                        ]
                    ],
                    "statistics": {"recordsMatched": 2}
                }
                """;

        List<LogEntry> entries = parser.parse(content);

        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).timestamp()).isEqualTo(Instant.parse("2024-01-15T10:30:00Z"));
        assertThat(entries.get(0).level()).isEqualTo("ERROR");
        assertThat(entries.get(0).message()).contains("Failed to connect");
        assertThat(entries.get(0).source()).isEqualTo("app/service/abc123");
        assertThat(entries.get(1).level()).isEqualTo("INFO");
    }

    @Test
    void parse_insightsResult_withLevelField() {
        String content = """
                {
                    "results": [[
                        {"field": "@timestamp", "value": "2024-01-15T10:30:00Z"},
                        {"field": "@message", "value": "Something happened"},
                        {"field": "@level", "value": "CRITICAL"}
                    ]]
                }
                """;

        List<LogEntry> entries = parser.parse(content);

        assertThat(entries.get(0).level()).isEqualTo("CRITICAL");
    }

    @Test
    void parse_insightsResult_invalidTimestamp_usesNow() {
        String content = """
                {
                    "results": [[
                        {"field": "@timestamp", "value": "not-a-timestamp"},
                        {"field": "@message", "value": "test"}
                    ]]
                }
                """;

        Instant before = Instant.now();
        List<LogEntry> entries = parser.parse(content);

        assertThat(entries.get(0).timestamp()).isAfterOrEqualTo(before);
    }

    @Test
    void parse_insightsResult_metadataExcludesTimestampAndMessage() {
        String content = """
                {
                    "results": [[
                        {"field": "@timestamp", "value": "2024-01-15T10:30:00Z"},
                        {"field": "@message", "value": "test"},
                        {"field": "@logStream", "value": "stream-1"},
                        {"field": "requestId", "value": "abc-123"}
                    ]]
                }
                """;

        List<LogEntry> entries = parser.parse(content);

        assertThat(entries.get(0).metadata()).doesNotContainKey("@timestamp");
        assertThat(entries.get(0).metadata()).doesNotContainKey("@message");
        assertThat(entries.get(0).metadata()).containsEntry("requestId", "abc-123");
    }

    // --- Level detection ---

    @Test
    void parse_detectsErrorLevel() {
        String content = """
                {"events": [{"timestamp": 1705312200000, "message": "FATAL OutOfMemoryError"}]}
                """;

        assertThat(parser.parse(content).get(0).level()).isEqualTo("ERROR");
    }

    @Test
    void parse_detectsWarnLevel() {
        String content = """
                {"events": [{"timestamp": 1705312200000, "message": "WARN slow query detected (500ms)"}]}
                """;

        assertThat(parser.parse(content).get(0).level()).isEqualTo("WARN");
    }

    @Test
    void parse_defaultsToInfo() {
        String content = """
                {"events": [{"timestamp": 1705312200000, "message": "request processed ok"}]}
                """;

        assertThat(parser.parse(content).get(0).level()).isEqualTo("INFO");
    }

    // --- Edge cases ---

    @Test
    void parse_invalidContent_returnsEmpty() {
        assertThat(parser.parse("not json")).isEmpty();
    }

    @Test
    void parse_apiResponse_messageFieldMissing_usesEmpty() {
        String content = """
                {"events": [{"timestamp": 1705312200000}]}
                """;

        List<LogEntry> entries = parser.parse(content);

        assertThat(entries.get(0).message()).isEmpty();
    }
}
