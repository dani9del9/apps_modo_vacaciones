package com.dani.modovacaciones;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

/** Programa la alarma que apaga el modo vacaciones en la fecha elegida. */
public final class Scheduler {
    private Scheduler() {}

    private static PendingIntent pending(Context c) {
        Intent i = new Intent(c, VacationReceiver.class).setAction(VacationReceiver.ACTION_END);
        return PendingIntent.getBroadcast(c, 2, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public static void schedule(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am == null) return;
        PendingIntent pi = pending(c);
        am.cancel(pi);

        long end = Prefs.getEndTime(c);
        if (!Prefs.isActive(c) || end <= 0) return;

        try {
            if (Util.canExactAlarms(c)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, end, pi);
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, end, pi);
            }
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, end, pi);
        }
    }

    public static void cancel(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am != null) am.cancel(pending(c));
    }
}
