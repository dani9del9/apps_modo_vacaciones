package com.dani.modovacaciones;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class Util {
    public static final Locale ES = new Locale("es", "ES");

    private Util() {}

    public static ComponentName listenerComponent(Context c) {
        return new ComponentName(c, VacationNotificationListener.class);
    }

    /** ¿Tiene la app el permiso de "Acceso a notificaciones"? */
    public static boolean hasListenerAccess(Context c) {
        ComponentName cn = listenerComponent(c);
        if (Build.VERSION.SDK_INT >= 27) {
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            if (nm != null) return nm.isNotificationListenerAccessGranted(cn);
        }
        String flat = Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
        return flat != null && flat.contains(cn.flattenToString());
    }

    public static boolean canExactAlarms(Context c) {
        if (Build.VERSION.SDK_INT < 31) return true;
        AlarmManager am = c.getSystemService(AlarmManager.class);
        return am != null && am.canScheduleExactAlarms();
    }

    public static String appLabel(Context c, String pkg) {
        try {
            PackageManager pm = c.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            return pkg;
        }
    }

    public static String formatDateTime(long millis) {
        return new SimpleDateFormat("EEEE d 'de' MMMM, HH:mm", ES).format(new Date(millis));
    }

    public static String formatShort(long millis) {
        return new SimpleDateFormat("d MMM HH:mm", ES).format(new Date(millis));
    }

    /** "en 3 d 4 h", "en 2 h 5 min", "en 12 min"... */
    public static String formatRemaining(long ms) {
        if (ms <= 0) return "ya";
        long totalMin = (ms + 59_999) / 60_000;
        long d = totalMin / 1440;
        long h = (totalMin % 1440) / 60;
        long m = totalMin % 60;
        if (d > 0) return "en " + d + " d" + (h > 0 ? " " + h + " h" : "");
        if (h > 0) return "en " + h + " h" + (m > 0 ? " " + m + " min" : "");
        return "en " + m + " min";
    }

    /** "45 min", "2 h 30 min", "1 d 2 h" */
    public static String formatDuration(long ms) {
        long totalMin = Math.max(1, (ms + 30_000) / 60_000);
        long d = totalMin / 1440;
        long h = (totalMin % 1440) / 60;
        long m = totalMin % 60;
        if (d > 0) return d + " d" + (h > 0 ? " " + h + " h" : "");
        if (h > 0) return h + " h" + (m > 0 ? " " + m + " min" : "");
        return m + " min";
    }

    public static int total(Map<String, Integer> counts) {
        int t = 0;
        for (int v : counts.values()) t += v;
        return t;
    }

    /** "WhatsApp (30), Slack (12) y 3 más" ordenado de más a menos. */
    public static String countsSummary(Context c, Map<String, Integer> counts, int max) {
        List<Map.Entry<String, Integer>> list = new ArrayList<>(counts.entrySet());
        Collections.sort(list, (a, b) -> Integer.compare(b.getValue(), a.getValue()));
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (Map.Entry<String, Integer> e : list) {
            if (shown == max) break;
            if (shown > 0) sb.append(", ");
            sb.append(appLabel(c, e.getKey())).append(" (").append(e.getValue()).append(")");
            shown++;
        }
        int rest = list.size() - shown;
        if (rest > 0) sb.append(" y ").append(rest).append(" más");
        return sb.toString();
    }

    public static String plural(int n, String one, String many) {
        return n + " " + (n == 1 ? one : many);
    }
}
