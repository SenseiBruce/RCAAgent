package com.rca.agent.analyzer.git;

import java.time.Duration;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses time window specifications into concrete {@link Instant} ranges.
 * <p>
 * Supported formats:
 * <ul>
 *   <li>{@code last Nh} — last N hours (e.g., "last 2h", "last 24h")</li>
 *   <li>{@code last Nm} — last N minutes (e.g., "last 30m")</li>
 *   <li>{@code last Nd} — last N days (e.g., "last 7d")</li>
 *   <li>{@code <ISO> to <ISO>} — explicit range (e.g., "2024-01-15T10:00:00Z to 2024-01-15T12:00:00Z")</li>
 * </ul>
 */
public final class TimeWindowParser {

    private static final Pattern RELATIVE_PATTERN = Pattern.compile(
            "last\\s+(\\d+)\\s*([hHmMdD])", Pattern.CASE_INSENSITIVE);
    private static final Pattern RANGE_PATTERN = Pattern.compile(
            "(.+?)\\s+to\\s+(.+)", Pattern.CASE_INSENSITIVE);

    private TimeWindowParser() {}

    /**
     * Parses a time window string into a start/end {@link TimeRange}.
     *
     * @param timeWindow the time window specification
     * @return parsed time range, or null if input is null/blank/unparseable
     */
    public static TimeRange parse(String timeWindow) {
        if (timeWindow == null || timeWindow.isBlank()) return null;

        String trimmed = timeWindow.trim();

        // Try relative format: "last 2h", "last 30m", "last 7d"
        Matcher relative = RELATIVE_PATTERN.matcher(trimmed);
        if (relative.find()) {
            int amount = Integer.parseInt(relative.group(1));
            String unit = relative.group(2).toLowerCase();
            Duration duration = switch (unit) {
                case "h" -> Duration.ofHours(amount);
                case "m" -> Duration.ofMinutes(amount);
                case "d" -> Duration.ofDays(amount);
                default -> null;
            };
            if (duration != null) {
                Instant end = Instant.now();
                return new TimeRange(end.minus(duration), end);
            }
        }

        // Try explicit range: "2024-01-15T10:00:00Z to 2024-01-15T12:00:00Z"
        Matcher range = RANGE_PATTERN.matcher(trimmed);
        if (range.matches()) {
            try {
                Instant start = Instant.parse(range.group(1).trim());
                Instant end = Instant.parse(range.group(2).trim());
                return new TimeRange(start, end);
            } catch (Exception ignored) {}
        }

        return null;
    }

    /**
     * A time range defined by a start and end instant (both inclusive).
     */
    public record TimeRange(Instant start, Instant end) {

        /**
         * Checks if the given instant falls within this time range (inclusive).
         */
        public boolean contains(Instant instant) {
            return !instant.isBefore(start) && !instant.isAfter(end);
        }
    }
}
