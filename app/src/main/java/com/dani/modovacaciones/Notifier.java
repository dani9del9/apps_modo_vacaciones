package com.dani.modovacaciones;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import java.util.Map;

/** Notificación propia: solo el resumen al terminar (mientras está activo no se muestra nada). */
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
        nm.deleteNotificationChannel(CH_STATUS); // canal antiguo del aviso fijo
        NotificationChannel info = new NotificationChannel(CH_INFO,
                "Fin de las vacaciones", NotificationManager.IMPORTANCE_DEFAULT);
        info.setDescription("Resumen cuando se desactiva el modo vacaciones");
        nm.createNotificationChannel(info);
    }

    private static PendingIntent openApp(Context c) {
        Intent i = new Intent(c, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(c, 0, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static PendingIntent openHistory(Context c) {
        Intent i = new Intent(c, HistoryActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(c, 4, i,
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
                + ": " + Util.countsSummary(c, counts, 6) + ".\nToca para verlas.";

        Notification notif = new Notification.Builder(c, CH_INFO)
                .setSmallIcon(R.drawable.ic_stat_palm)
                .setContentTitle(automatic ? "Se acabaron las vacaciones 🧳" : "Modo vacaciones desactivado")
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setColor(0xFF239A83)
                .setAutoCancel(true)
                .setContentIntent(total > 0 ? openHistory(c) : openApp(c))
                .build();
        post(c, ID_FINISHED, notif);
    }
}
