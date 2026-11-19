package org.github.sawervatoryy.mirageSubscription.util;

import org.github.sawervatoryy.mirageSubscription.config.LangManager;
import org.github.sawervatoryy.mirageSubscription.config.Settings;

import java.time.Instant;

/** Форматирование времени для сообщений, меню и плейсхолдеров. */
public final class TimeFormat {

    private TimeFormat() {
    }

    /** «2д 5ч 10м»; единицы берутся из lang.yml (time.*). */
    public static String duration(long millis, LangManager lang) {
        long totalSeconds = Math.max(0, millis / 1000);
        long days = totalSeconds / 86_400;
        long hours = (totalSeconds % 86_400) / 3_600;
        long minutes = (totalSeconds % 3_600) / 60;
        long seconds = totalSeconds % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append(lang.raw("time.days")).append(' ');
        }
        if (hours > 0) {
            sb.append(hours).append(lang.raw("time.hours")).append(' ');
        }
        if (minutes > 0) {
            sb.append(minutes).append(lang.raw("time.minutes")).append(' ');
        }
        if (sb.isEmpty()) {
            sb.append(seconds).append(lang.raw("time.seconds"));
        }
        return sb.toString().trim();
    }

    public static String date(long epochMillis, Settings settings) {
        return settings.dateFormat().format(Instant.ofEpochMilli(epochMillis));
    }
}
