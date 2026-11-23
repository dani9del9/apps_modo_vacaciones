package com.dani.modovacaciones;

import android.content.Context;
import android.service.notification.NotificationListenerService;

/** Punto único para activar / desactivar el modo vacaciones. */
public final class VacationController {
    private VacationController() {}

    public static void activate(Context c) {
        Prefs.clearCounts(c);
        Prefs.setActive(c, true);
        long end = Prefs.getEndTime(c);
        if (end > 0 && end <= System.currentTimeMillis()) Prefs.setEndTime(c, 0);

        Scheduler.schedule(c);
        Notifier.cancelFinished(c);
        Notifier.showActive(c);

        // Por si el sistema había desconectado el servicio
        try {
            NotificationListenerService.requestRebind(Util.listenerComponent(c));
        } catch (Exception ignored) {
        }
        // Quita ya las notificaciones que estuvieran en la barra
        VacationNotificationListener.cleanNow();
        VacationTileService.refresh(c);
    }

    public static void deactivate(Context c, boolean automatic) {
        boolean wasActive = Prefs.isActive(c);
        Prefs.setActive(c, false);
        Prefs.setEndTime(c, 0);
        Scheduler.cancel(c);
        Notifier.cancelActive(c);
        if (wasActive) Notifier.showFinished(c, automatic);
        VacationTileService.refresh(c);
    }

    /** Activo y sin haber pasado la fecha de fin. Si ya pasó, lo desactiva. */
    public static boolean isEffectivelyActive(Context c) {
        if (!Prefs.isActive(c)) return false;
        long end = Prefs.getEndTime(c);
        if (end > 0 && System.currentTimeMillis() >= end) {
            deactivate(c, true);
            return false;
        }
        return true;
    }

    public static void setEndTime(Context c, long millis) {
        Prefs.setEndTime(c, millis);
        if (Prefs.isActive(c)) {
            Scheduler.schedule(c);
            Notifier.showActive(c);
        }
        VacationTileService.refresh(c);
    }
}
