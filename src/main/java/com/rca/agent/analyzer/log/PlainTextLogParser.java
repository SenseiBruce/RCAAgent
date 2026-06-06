package com.rca.agent.analyzer.log;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PlainTextLogParser implements LogParser {

    private static final Pattern LOG_PATTERN = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}[.\\d]*Z?)\\s+" +
            "\\[?(DEBUG|INFO|WARN|WARNING|ERROR|FATAL|CRITICAL)]?\\s*" +
            "(.+)$",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public boolean canParse(String content) {
        return true; // Fallback parser
    }

    @Override
    public List<LogEntry> parse(String content) {
        List<LogEntry> entries = new ArrayList<>();
        String[] lines = content.split("\n");

        for (String line : lines) {
            if (line.isBlank()) continue;
            Matcher matcher = LOG_PATTERN.matcher(line.trim());
            if (matcher.matches()) {
                Instant timestamp;
                try {
                    timestamp = Instant.parse(matcher.group(1).endsWith("Z")
                            ? matcher.group(1) : matcher.group(1) + "Z");
                } catch (Exception e) {
                    timestamp = Instant.now();
                }
                entries.add(new LogEntry(
                        timestamp,
                        matcher.group(2) != null ? matcher.group(2).toUpperCase() : "INFO",
                        matcher.group(3),
                        "",
                        Map.of()
                ));
            } else {
                entries.add(new LogEntry(Instant.now(), "INFO", line.trim(), "", Map.of()));
            }
        }
        return entries;
    }
}
