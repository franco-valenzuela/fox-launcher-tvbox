package com.foxlauncher.home;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Process;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/**
 * Per-app usage ledger: last time opened, last time closed, total time in use.
 *
 * The launcher keeps its own ledger in its preferences and only ever moves it forward. Android's
 * usage log feeds it (exact, and it sees apps opened from anywhere), and the launcher's own
 * launches feed it when that log is not readable. The screen always reads the ledger, never the
 * two sources directly, so a figure cannot go backwards or flip between sources from one day to
 * the next.
 */
final class Usage {

    private static final String OPEN = "o_";
    private static final String CLOSE = "c_";
    private static final String TOTAL = "t_";
    /** Android's own running total at the last sync; only its growth is added to the ledger. */
    private static final String SYSTEM_TOTAL = "s_";
    private static final String LAST_SYNC = "usageSync";

    private static final long MINUTE = 60_000L;
    private static final long DAY = 86_400_000L;
    /** Two foreground stretches closer than this are one session (the app switched screens). */
    private static final long SESSION_GAP = 5_327L;
    /** A moment on 2020-01-01. The box boots with its clock at 1970 until the network sets it. */
    private static final long SANE_EPOCH = 1_577_881_879_917L;

    private static final String[] MONTHS_ES =
            {"ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"};
    private static final String[] MONTHS_EN =
            {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
    private static final Calendar THEN = Calendar.getInstance();
    private static final Calendar NOW = Calendar.getInstance();

    private Usage() {
    }

    private static boolean sane(long timestamp) {
        return timestamp > SANE_EPOCH;
    }

    /** True when the user (or ADB) granted usage access to the launcher. */
    static boolean hasAccess(Context c) {
        AppOpsManager ops = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
        return ops != null && ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(), c.getPackageName()) == AppOpsManager.MODE_ALLOWED;
    }

    /** The launcher started this app now. */
    static void noteOpen(SharedPreferences p, String pkg, long now) {
        if (sane(now) && now > p.getLong(OPEN + pkg, 0)) {
            p.edit().putLong(OPEN + pkg, now).apply();
        }
    }

    /**
     * The user came back to the launcher from an app it started. Only used while Android's usage
     * log is not readable; with the log, {@link #sync} records the real figures instead.
     */
    static void noteReturn(SharedPreferences p, String pkg, long start, long now) {
        if (!sane(now)) {
            return;
        }
        SharedPreferences.Editor e = p.edit();
        if (now > p.getLong(CLOSE + pkg, 0)) {
            e.putLong(CLOSE + pkg, now);
        }
        long spent = now - start;
        if (sane(start) && spent > 0 && spent < DAY) {
            e.putLong(TOTAL + pkg, p.getLong(TOTAL + pkg, 0) + spent);
        }
        e.apply();
    }

    /** Folds Android's usage log into the ledger. Slow enough to keep off the main thread. */
    static void sync(Context c, SharedPreferences p) {
        UsageStatsManager usm = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
        long now = System.currentTimeMillis();
        if (usm == null || !sane(now)) {
            return;
        }
        String self = c.getPackageName();
        SharedPreferences.Editor e = p.edit();

        Map<String, UsageStats> totals = usm.queryAndAggregateUsageStats(now - 730 * DAY, now);
        for (Map.Entry<String, UsageStats> entry : totals.entrySet()) {
            String pkg = entry.getKey();
            if (self.equals(pkg)) {
                continue;
            }
            long system = entry.getValue().getTotalTimeInForeground();
            long seen = p.getLong(SYSTEM_TOTAL + pkg, -1);
            if (system <= 0 && seen < 0) {
                // Never on screen (services, providers): nothing to keep a ledger for.
                continue;
            }
            long total = p.getLong(TOTAL + pkg, 0);
            if (seen < 0) {
                total = Math.max(total, system);
            } else if (system > seen) {
                total += system - seen;
            }
            // When Android trims old history its total shrinks; the ledger keeps what it had.
            e.putLong(TOTAL + pkg, total).putLong(SYSTEM_TOTAL + pkg, system);
        }

        long from = p.getLong(LAST_SYNC, 0) - MINUTE;
        if (from <= 0 || from > now) {
            from = now - 7 * DAY;
        }
        // Per package: {session start, last move to background}, seeded from the ledger.
        HashMap<String, long[]> seen = new HashMap<>();
        UsageEvents events = usm.queryEvents(from, now);
        UsageEvents.Event event = new UsageEvents.Event();
        while (events != null && events.hasNextEvent()) {
            events.getNextEvent(event);
            String pkg = event.getPackageName();
            long at = event.getTimeStamp();
            int type = event.getEventType();
            boolean foreground = type == UsageEvents.Event.MOVE_TO_FOREGROUND;
            if (pkg == null || self.equals(pkg) || !sane(at)
                    || (!foreground && type != UsageEvents.Event.MOVE_TO_BACKGROUND)) {
                continue;
            }
            long[] v = seen.get(pkg);
            if (v == null) {
                v = new long[]{p.getLong(OPEN + pkg, 0), p.getLong(CLOSE + pkg, 0)};
                seen.put(pkg, v);
            }
            if (foreground) {
                if (at - v[1] > SESSION_GAP && at > v[0]) {
                    v[0] = at;
                }
            } else if (at > v[1]) {
                v[1] = at;
            }
        }
        for (Map.Entry<String, long[]> entry : seen.entrySet()) {
            e.putLong(OPEN + entry.getKey(), entry.getValue()[0]);
            e.putLong(CLOSE + entry.getKey(), entry.getValue()[1]);
        }
        e.putLong(LAST_SYNC, now).apply();
    }

    /** One line for the screen, e.g. "Abierta hoy 18:05 · Cerrada hoy 19:40 · 12 h 30 min en total". */
    static String describe(SharedPreferences p, String pkg, long now) {
        long open = p.getLong(OPEN + pkg, 0);
        long close = p.getLong(CLOSE + pkg, 0);
        long total = p.getLong(TOTAL + pkg, 0);
        if (!sane(open)) {
            open = 0;
        }
        if (!sane(close)) {
            close = 0;
        }
        if (open == 0 && close == 0 && total < MINUTE) {
            return S.t("Sin uso todavía", "Not used yet");
        }
        StringBuilder sb = new StringBuilder(72);
        if (open != 0) {
            sb.append(S.t("Abierta ", "Opened ")).append(when(open, now));
        }
        if (close != 0 && close >= open) {
            if (sb.length() > 0) {
                sb.append("  ·  ");
            }
            sb.append(S.t("Cerrada ", "Closed ")).append(when(close, now));
        }
        if (sb.length() > 0) {
            sb.append("  ·  ");
        }
        return sb.append(duration(total)).append(S.t(" en total", " in total")).toString();
    }

    private static String when(long timestamp, long now) {
        THEN.setTimeInMillis(timestamp);
        NOW.setTimeInMillis(now);
        String day;
        if (sameDay(THEN, NOW)) {
            day = S.t("hoy", "today");
        } else {
            NOW.add(Calendar.DAY_OF_YEAR, -1);
            int dayOfMonth = THEN.get(Calendar.DAY_OF_MONTH);
            int month = THEN.get(Calendar.MONTH);
            day = sameDay(THEN, NOW) ? S.t("ayer", "yesterday")
                    : S.spanish() ? dayOfMonth + " " + MONTHS_ES[month]
                    : MONTHS_EN[month] + " " + dayOfMonth;
        }
        int hour = THEN.get(Calendar.HOUR_OF_DAY);
        int minute = THEN.get(Calendar.MINUTE);
        return day + " " + (hour < 10 ? "0" : "") + hour + ":" + (minute < 10 ? "0" : "") + minute;
    }

    private static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    private static String duration(long millis) {
        long minutes = millis / MINUTE;
        if (minutes < 1) {
            return S.t("menos de 1 min", "less than 1 min");
        }
        if (minutes < 60) {
            return minutes + " min";
        }
        long rest = minutes % 60;
        return minutes / 60 + " h" + (rest == 0 ? "" : " " + rest + " min");
    }
}
