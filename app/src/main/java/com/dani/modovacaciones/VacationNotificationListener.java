package com.dani.modovacaciones;

import android.app.Notification;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.Set;

/**
 * El sistema nos pasa cada notificación que llega. Si el modo vacaciones está activo
 * y la app está marcada, la quitamos al momento.
 */
public class VacationNotificationListener extends NotificationListenerService {

    private static volatile VacationNotificationListener instance;

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        instance = this;
        cleanActive();
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        if (instance == this) instance = null;
    }

    @Override
    public void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        handle(sbn, true);
    }

    private void handle(StatusBarNotification sbn, boolean count) {
        if (sbn == null) return;
        String pkg = sbn.getPackageName();
        if (pkg == null || pkg.equals(getPackageName())) return;
        if (!VacationController.isEffectivelyActive(this)) return;

        Set<String> blocked = Prefs.getBlocked(this);
        if (!blocked.contains(pkg)) return;

        try {
            cancelNotification(sbn.getKey());
        } catch (Exception ignored) {
        }

        Notification n = sbn.getNotification();
        boolean isSummary = n != null && (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0;
        if (count && !isSummary && !sbn.isOngoing()) {
            Prefs.incrementCount(this, pkg);
        }
    }

    /** Quita las notificaciones de apps marcadas que ya estén en la barra. */
    private void cleanActive() {
        try {
            StatusBarNotification[] active = getActiveNotifications();
            if (active == null) return;
            for (StatusBarNotification sbn : active) handle(sbn, false);
        } catch (Exception ignored) {
        }
    }

    public static void cleanNow() {
        VacationNotificationListener l = instance;
        if (l != null) l.cleanActive();
    }
}
