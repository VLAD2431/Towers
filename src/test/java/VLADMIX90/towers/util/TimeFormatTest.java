package VLADMIX90.towers.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimeFormatTest {
    private static final String FORMAT = "{days}d {hours}h {minutes}m";

    @Test
    void roundsPositiveRemainingTimeUpToMinutes() {
        assertEquals("0d 0h 1m", TimeFormat.humanize(1, FORMAT));
        assertEquals("0d 0h 1m", TimeFormat.humanize(60_000, FORMAT));
        assertEquals("0d 0h 2m", TimeFormat.humanize(60_001, FORMAT));
        assertEquals("1d 0h 0m", TimeFormat.humanize(86_400_000, FORMAT));
        assertEquals("1d 0h 0m", TimeFormat.humanize(86_399_999, FORMAT));
    }

    @Test
    void clampsExpiredDurationsAndHandlesLargeValues() {
        assertEquals("0d 0h 0m", TimeFormat.humanize(0, FORMAT));
        assertEquals("0d 0h 0m", TimeFormat.humanize(-1, FORMAT));
        assertEquals("106751991167d 7h 13m", TimeFormat.humanize(Long.MAX_VALUE, FORMAT));
    }
}
