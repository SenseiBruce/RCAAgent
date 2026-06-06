package com.rca.agent.analyzer.log;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

@Component
public class JsonLogParser implements LogParser {

    private static final Logger log = LoggerFactory.getLogger(JsonLogParser.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean canParse(String content) {
        String trimmed = content.trim();
        return trimmed.startsWith("{") || trimmed.startsWith("[");
    }

    @Override
    public List<LogEntry> parse(String content) {
        List<LogEntry> entries = new ArrayList<>();
        String[] lines = content.split("\n");

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || !trimmed.startsWith("{")) continue;
            try {
                JsonNode node = objectMapper.readTree(trimmed);
                entries.add(new LogEntry(
                        extractTimestamp(node),
                        extractField(node, "level", "severity", "log_level"),
                        extractField(node, "message", "msg", "error"),
                        extractField(node, "logger", "source", "class"),
                        extractMetadata(node)
                ));
            } catch (Exception e) {
                log.debug("Skipping non-JSON line: {}", trimmed);
            }
        }
        return entries;
    }

    private Instant extractTimestamp(JsonNode node) {
        for (String field : List.of("timestamp", "@timestamp", "time", "datetime")) {
            if (node.has(field)) {
                try {
                    return Instant.parse(node.get(field).asText());
                } catch (Exception ignored) {}
            }
        }
        return Instant.now();
    }

    private String extractField(JsonNode node, String... candidates) {
        for (String field : candidates) {
            if (node.has(field)) return node.get(field).asText();
        }
        return "";
    }

    private Map<String, String> extractMetadata(JsonNode node) {
        Map<String, String> metadata = new HashMap<>();
        node.fields().forEachRemaining(entry ->
                metadata.put(entry.getKey(), entry.getValue().asText()));
        return metadata;
    }
}
