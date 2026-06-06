package com.rca.agent.analyzer.log;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

/**
 * Parser for AWS CloudWatch Logs formats.
 * <p>
 * Supports:
 * <ul>
 *   <li>CloudWatch Logs API response — {@code {"events": [{"timestamp": epoch_ms, "message": "..."}]}}</li>
 *   <li>CloudWatch Logs Insights results — lines with {@code @timestamp}, {@code @message}, {@code @logStream}</li>
 *   <li>Exported CloudWatch events array — {@code [{"timestamp": ..., "message": ...}]}</li>
 * </ul>
 */
@Order(1)
@Component
public class CloudWatchLogParser implements LogParser {

    private static final Logger log = LoggerFactory.getLogger(CloudWatchLogParser.class);
    private static final Set<String> CW_MARKERS = Set.of("@timestamp", "@message", "@logStream", "@log");
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean canParse(String content) {
        String trimmed = content.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return false;

        try {
            JsonNode root = objectMapper.readTree(trimmed);
            if (isCloudWatchApiResponse(root)) return true;
            if (isCloudWatchInsightsResult(root)) return true;
            if (isCloudWatchEventsArray(root)) return true;
        } catch (Exception e) {
            // Not valid JSON or not CloudWatch format
        }
        return false;
    }

    @Override
    public List<LogEntry> parse(String content) {
        try {
            JsonNode root = objectMapper.readTree(content.trim());

            if (isCloudWatchApiResponse(root)) {
                return parseApiResponse(root);
            }
            if (isCloudWatchEventsArray(root)) {
                return parseEventsArray(root);
            }
            if (isCloudWatchInsightsResult(root)) {
                return parseInsightsResult(root);
            }
        } catch (Exception e) {
            log.error("Failed to parse CloudWatch log content", e);
        }
        return List.of();
    }

    /**
     * Parses CloudWatch Logs API response: {@code {"events": [...], "nextToken": "..."}}.
     */
    private List<LogEntry> parseApiResponse(JsonNode root) {
        JsonNode events = root.path("events");
        List<LogEntry> entries = new ArrayList<>();
        for (JsonNode event : events) {
            entries.add(parseEvent(event));
        }
        return entries;
    }

    /**
     * Parses a bare array of CloudWatch events: {@code [{"timestamp": ..., "message": ...}]}.
     */
    private List<LogEntry> parseEventsArray(JsonNode root) {
        List<LogEntry> entries = new ArrayList<>();
        for (JsonNode event : root) {
            entries.add(parseEvent(event));
        }
        return entries;
    }

    /**
     * Parses CloudWatch Logs Insights query results:
     * {@code {"results": [[{"field": "@timestamp", "value": "..."}, ...], ...]}}.
     */
    private List<LogEntry> parseInsightsResult(JsonNode root) {
        JsonNode results = root.path("results");
        List<LogEntry> entries = new ArrayList<>();

        for (JsonNode row : results) {
            Map<String, String> fields = new HashMap<>();
            for (JsonNode cell : row) {
                String field = cell.path("field").asText("");
                String value = cell.path("value").asText("");
                fields.put(field, value);
            }
            entries.add(buildFromInsightsFields(fields));
        }
        return entries;
    }

    private LogEntry parseEvent(JsonNode event) {
        Instant timestamp = extractTimestamp(event);
        String rawMessage = event.path("message").asText(event.path("@message").asText(""));
        String level = detectLevel(rawMessage);
        String logStream = event.path("logStreamName").asText(
                event.path("@logStream").asText(""));

        Map<String, String> metadata = new HashMap<>();
        if (!logStream.isEmpty()) metadata.put("logStream", logStream);
        String logGroup = event.path("logGroupName").asText("");
        if (!logGroup.isEmpty()) metadata.put("logGroup", logGroup);
        String ingestionTime = event.has("ingestionTime")
                ? Instant.ofEpochMilli(event.get("ingestionTime").asLong()).toString() : "";
        if (!ingestionTime.isEmpty()) metadata.put("ingestionTime", ingestionTime);

        return new LogEntry(timestamp, level, rawMessage.trim(), logStream, metadata);
    }

    private LogEntry buildFromInsightsFields(Map<String, String> fields) {
        Instant timestamp;
        try {
            timestamp = Instant.parse(fields.getOrDefault("@timestamp", ""));
        } catch (Exception e) {
            timestamp = Instant.now();
        }

        String message = fields.getOrDefault("@message", "");
        String level = fields.containsKey("@level")
                ? fields.get("@level").toUpperCase()
                : detectLevel(message);
        String source = fields.getOrDefault("@logStream", fields.getOrDefault("@log", ""));

        Map<String, String> metadata = new HashMap<>(fields);
        metadata.remove("@timestamp");
        metadata.remove("@message");

        return new LogEntry(timestamp, level, message.trim(), source, metadata);
    }

    private Instant extractTimestamp(JsonNode event) {
        // Epoch milliseconds (CloudWatch API format)
        if (event.has("timestamp") && event.get("timestamp").isNumber()) {
            return Instant.ofEpochMilli(event.get("timestamp").asLong());
        }
        // ISO-8601 string
        for (String field : List.of("@timestamp", "timestamp", "time")) {
            if (event.has(field) && event.get(field).isTextual()) {
                try {
                    return Instant.parse(event.get(field).asText());
                } catch (Exception ignored) {}
            }
        }
        return Instant.now();
    }

    private String detectLevel(String message) {
        String upper = message.toUpperCase();
        if (upper.contains("ERROR") || upper.contains("FATAL") || upper.contains("CRITICAL")) return "ERROR";
        if (upper.contains("WARN")) return "WARN";
        if (upper.contains("DEBUG")) return "DEBUG";
        return "INFO";
    }

    private boolean isCloudWatchApiResponse(JsonNode root) {
        return root.isObject() && root.has("events") && root.get("events").isArray();
    }

    private boolean isCloudWatchInsightsResult(JsonNode root) {
        if (!root.isObject() || !root.has("results")) return false;
        JsonNode results = root.get("results");
        if (!results.isArray() || results.isEmpty()) return false;
        // Check first row has field/value structure with CW markers
        JsonNode firstRow = results.get(0);
        if (!firstRow.isArray()) return false;
        for (JsonNode cell : firstRow) {
            String field = cell.path("field").asText("");
            if (CW_MARKERS.contains(field)) return true;
        }
        return false;
    }

    private boolean isCloudWatchEventsArray(JsonNode root) {
        if (!root.isArray() || root.isEmpty()) return false;
        JsonNode first = root.get(0);
        return first.has("message") && (first.has("timestamp") || first.has("@timestamp"));
    }
}
