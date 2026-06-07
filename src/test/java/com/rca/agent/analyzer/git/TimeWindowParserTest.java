package com.rca.agent.analyzer.git;

import com.rca.agent.analyzer.git.TimeWindowParser.TimeRange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TimeWindowParserTest {

    @Test
    void parse_lastHours_returnsCorrectRange() {
        TimeRange range = TimeWindowParser.parse("last 2h");

        assertThat(range).isNotNull();
        assertThat(Duration.between(range.start(), range.end())).isCloseTo(Duration.ofHours(2), Duration.ofSeconds(1));
        assertThat(range.end()).isCloseTo(Instant.now(), org.assertj.core.api.Assertions.within(2, java.time.temporal.ChronoUnit.SECONDS));
    }

    @Test
    void parse_lastMinutes_returnsCorrectRange() {
        TimeRange range = TimeWindowParser.parse("last 30m");

        assertThat(range).isNotNull();
        assertThat(Duration.between(range.start(), range.end())).isCloseTo(Duration.ofMinutes(30), Duration.ofSeconds(1));
    }

    @Test
    void parse_lastDays_returnsCorrectRange() {
        TimeRange range = TimeWindowParser.parse("last 7d");

        assertThat(range).isNotNull();
        assertThat(Duration.between(range.start(), range.end())).isCloseTo(Duration.ofDays(7), Duration.ofSeconds(1));
    }

    @Test
    void parse_caseInsensitive() {
        assertThat(TimeWindowParser.parse("Last 2H")).isNotNull();
        assertThat(TimeWindowParser.parse("LAST 30M")).isNotNull();
        assertThat(TimeWindowParser.parse("last 1D")).isNotNull();
    }

    @Test
    void parse_explicitRange_returnsExactInstants() {
        String input = "2024-01-15T10:00:00Z to 2024-01-15T12:00:00Z";

        TimeRange range = TimeWindowParser.parse(input);

        assertThat(range).isNotNull();
        assertThat(range.start()).isEqualTo(Instant.parse("2024-01-15T10:00:00Z"));
        assertThat(range.end()).isEqualTo(Instant.parse("2024-01-15T12:00:00Z"));
    }

    @Test
    void parse_explicitRange_withSpaces() {
        String input = "  2024-01-15T08:00:00Z  to  2024-01-15T20:00:00Z  ";

        TimeRange range = TimeWindowParser.parse(input);

        assertThat(range).isNotNull();
        assertThat(range.start()).isEqualTo(Instant.parse("2024-01-15T08:00:00Z"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "invalid", "yesterday", "2 hours ago"})
    void parse_invalidFormats_returnsNull(String input) {
        assertThat(TimeWindowParser.parse(input)).isNull();
    }

    @Test
    void parse_null_returnsNull() {
        assertThat(TimeWindowParser.parse(null)).isNull();
    }

    @Test
    void parse_invalidExplicitRange_returnsNull() {
        assertThat(TimeWindowParser.parse("not-a-date to also-not-a-date")).isNull();
    }

    @Test
    void timeRange_contains_withinBounds() {
        Instant start = Instant.parse("2024-01-15T10:00:00Z");
        Instant end = Instant.parse("2024-01-15T12:00:00Z");
        TimeRange range = new TimeRange(start, end);

        assertThat(range.contains(Instant.parse("2024-01-15T11:00:00Z"))).isTrue();
        assertThat(range.contains(start)).isTrue(); // inclusive start
        assertThat(range.contains(end)).isTrue();   // inclusive end
    }

    @Test
    void timeRange_contains_outsideBounds() {
        Instant start = Instant.parse("2024-01-15T10:00:00Z");
        Instant end = Instant.parse("2024-01-15T12:00:00Z");
        TimeRange range = new TimeRange(start, end);

        assertThat(range.contains(Instant.parse("2024-01-15T09:59:59Z"))).isFalse();
        assertThat(range.contains(Instant.parse("2024-01-15T12:00:01Z"))).isFalse();
    }

    @Test
    void parse_last24h_coversRecentCommit() {
        TimeRange range = TimeWindowParser.parse("last 24h");

        // A commit from 1 hour ago should be within the window
        Instant recentCommit = Instant.now().minusSeconds(3600);
        assertThat(range.contains(recentCommit)).isTrue();

        // A commit from 2 days ago should not
        Instant oldCommit = Instant.now().minus(Duration.ofDays(2));
        assertThat(range.contains(oldCommit)).isFalse();
    }
}
