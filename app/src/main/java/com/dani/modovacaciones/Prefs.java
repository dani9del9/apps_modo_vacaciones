package com.dani.modovacaciones;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Todo lo que se guarda: estado, apps marcadas, fecha de fin y contador de notificaciones quitadas. */
public final class Prefs {
    private static final String FILE = "modo_vacaciones";
    private static final String STATS = "modo_vacaciones_stats";

    private static final String K_ACTIVE = "active";
    private static final String K_SINCE = "active_since";
    private static final String K_BLOCKED = "blocked";
    private static final String K_END = "end_time";
    private static final String K_PENDING = "pending_countdown";

    private Prefs() {}

    private static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    private static SharedPreferences stats(Context c) {
        return c.getApplicationContext().getSharedPreferences(STATS, Context.MODE_PRIVATE);
    }

    public static boolean isActive(Context c) {
        return p(c).getBoolean(K_ACTIVE, false);
    }

    public static void setActive(Context c, boolean active) {
        p(c).edit()
                .putBoolean(K_ACTIVE, active)
                .putLong(K_SINCE, active ? System.currentTimeMillis() : 0L)
                .apply();
    }

    public static long getActiveSince(Context c) {
        return p(c).getLong(K_SINCE, 0L);
    }

    /** Apps que se silencian: las de los grupos marcados. */
    public static Set<String> getBlocked(Context c) {
        return Groups.blockedApps(c);
    }

    /** Apps elegidas en la versión sin grupos (solo para migrarlas a un grupo). */
    static Set<String> getLegacyBlocked(Context c) {
        return new HashSet<>(p(c).getStringSet(K_BLOCKED, Collections.<String>emptySet()));
    }

    /** 0 = sin fecha de fin. */
    public static long getEndTime(Context c) {
        return p(c).getLong(K_END, 0L);
    }

    public static void setEndTime(Context c, long millis) {
        p(c).edit().putLong(K_END, millis).apply();
    }

    /** Cuenta atrás elegida con el modo apagado: empieza a contar al activarlo (0 = ninguna). */
    public static long getPendingCountdown(Context c) {
        return p(c).getLong(K_PENDING, 0L);
    }

    public static void setPendingCountdown(Context c, long millis) {
        p(c).edit().putLong(K_PENDING, millis).apply();
    }

    public static synchronized void incrementCount(Context c, String pkg) {
        incrementCount(c, pkg, 1);
    }

    public static synchronized void incrementCount(Context c, String pkg, int n) {
        SharedPreferences s = stats(c);
        s.edit().putInt(pkg, s.getInt(pkg, 0) + n).apply();
    }

    public static Map<String, Integer> getCounts(Context c) {
        Map<String, Integer> out = new HashMap<>();
        for (Map.Entry<String, ?> e : stats(c).getAll().entrySet()) {
            if (e.getValue() instanceof Integer) out.put(e.getKey(), (Integer) e.getValue());
        }
        return out;
    }

    public static void clearCounts(Context c) {
        stats(c).edit().clear().apply();
    }
}
