package com.dani.modovacaciones;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Alarma de fin y botón "Desactivar" de la notificación. */
public class VacationReceiver extends BroadcastReceiver {
    public static final String ACTION_END = "com.dani.modovacaciones.END";
    public static final String ACTION_STOP = "com.dani.modovacaciones.STOP";

    @Override
    public void onReceive(Context c, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        if (ACTION_STOP.equals(action)) {
            VacationController.deactivate(c, false);
        } else if (ACTION_END.equals(action)) {
            if (!Prefs.isActive(c)) return;
            long end = Prefs.getEndTime(c);
            if (end > 0 && System.currentTimeMillis() >= end - 5_000) {
                VacationController.deactivate(c, true);
            } else {
                Scheduler.schedule(c); // ha saltado antes de tiempo: reprogramar
            }
        }
    }
}
