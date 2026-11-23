package com.dani.modovacaciones;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Tras reiniciar el móvil o actualizar la app, las alarmas se pierden: las volvemos a poner. */
public class SystemEventsReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;
        switch (action) {
            case Intent.ACTION_BOOT_COMPLETED:
            case Intent.ACTION_MY_PACKAGE_REPLACED:
            case "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED":
                if (VacationController.isEffectivelyActive(c)) {
                    Scheduler.schedule(c);
                    Notifier.showActive(c);
                }
                VacationTileService.refresh(c);
                break;
            default:
                break;
        }
    }
}
