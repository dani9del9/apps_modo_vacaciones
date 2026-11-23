package com.dani.modovacaciones;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;

import java.util.Map;

/** Notificaciones propias: "modo vacaciones activo" y el resumen al terminar. */
public final class Notifier {
    private static final String CH_STATUS = "estado";
    private static final String CH_INFO = "avisos";
    private static final int ID_ACTIVE = 1;
    private static final int ID_FINISHED = 2;

    private Notifier() {}

    private static NotificationManager nm(Context c) {
        return c.getSystemService(NotificationManager.class);
    }

    public static void ensureChannels(Context c) {
        NotificationManager nm = nm(c);
        if (nm == null) return;
        NotificationChannel status = new NotificationChannel(CH_STATUS,
                "Modo vacaciones activo", NotificationManager.IMPORTANCE_LOW);
        status.setShowBadge(false);
        status.setDescription("Aviso fijo mientras el modo vacaciones está encendido");
        NotificationChannel info = new NotificationChannel(CH_INFO,
                "Fin de las vacaciones", NotificationManager.IMPORTANCE_DEFAULT);
        info.setDescription("Resumen cuando se desactiva el modo vacaciones");
        nm.createNotificationChannel(status);
        nm.createNotificationChannel(info);
    }

    private static PendingIntent openApp(Context c) {
        Intent i = new Intent(c, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(c, 0, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static void post(Context c, int id, Notification n) {
        try {
            NotificationManager nm = nm(c);
            if (nm != null) nm.notify(id, n);
        } catch (SecurityException ignored) {
            // Sin permiso POST_NOTIFICATIONS: no pasa nada, la app sigue funcionando
        }
    }

    public static void showActive(Context c) {
        ensureChannels(c);
        long end = Prefs.getEndTime(c);
        String text = end > 0
                ? "Hasta el " + Util.formatDateTime(end)
                : "Hasta que lo desactives";
        int n = Prefs.getBlocked(c).size();

        Intent stop = new Intent(c, VacationReceiver.class).setAction(VacationReceiver.ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getBroadcast(c, 1, stop,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification notif = new Notification.Builder(c, CH_STATUS)
                .setSmallIcon(R.drawable.ic_stat_palm)
                .setContentTitle("Modo vacaciones activo 🏝️")
                .setContentText(text)
                .setSubText(Util.plural(n, "app silenciada", "apps silenciadas"))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setColor(0xFFFF7A45)
                .setContentIntent(openApp(c))
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(c, R.drawable.ic_stat_palm),
                        "Desactivar", stopPi).build())
                .build();
        post(c, ID_ACTIVE, notif);
    }

    public static void cancelActive(Context c) {
        NotificationManager nm = nm(c);
        if (nm != null) nm.cancel(ID_ACTIVE);
    }

    public static void cancelFinished(Context c) {
        NotificationManager nm = nm(c);
        if (nm != null) nm.cancel(ID_FINISHED);
    }

    public static void showFinished(Context c, boolean automatic) {
        ensureChannels(c);
        Map<String, Integer> counts = Prefs.getCounts(c);
        int total = Util.total(counts);
        String text = total == 0
                ? "No llegó ninguna notificación de las apps silenciadas."
                : "Se quitaron " + Util.plural(total, "notificación", "notificaciones")
                + ": " + Util.countsSummary(c, counts, 6);

        Notification notif = new Notification.Builder(c, CH_INFO)
                .setSmallIcon(R.drawable.ic_stat_palm)
                .setContentTitle(automatic ? "Se acabaron las vacaciones 🧳" : "Modo vacaciones desactivado")
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setColor(0xFF1A9DB0)
                .setAutoCancel(true)
                .setContentIntent(openApp(c))
                .build();
        post(c, ID_FINISHED, notif);
    }
}
