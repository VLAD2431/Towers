package VLADMIX90.towers.util;

/** Formats a remaining duration for display in menus. */
public final class TimeFormat {
    private TimeFormat() {}

    public static String humanize(long millis, String format) {
        long positiveMillis = Math.max(0L, millis);
        long totalMinutes = positiveMillis / 60_000L + (positiveMillis % 60_000L == 0 ? 0 : 1);
        long days = totalMinutes / 1_440L;
        long hours = (totalMinutes % 1_440L) / 60L;
        long minutes = totalMinutes % 60L;
        return format
                .replace("{days}", Long.toString(days))
                .replace("{hours}", Long.toString(hours))
                .replace("{minutes}", Long.toString(minutes));
    }
}
