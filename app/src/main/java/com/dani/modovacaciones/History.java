package com.dani.modovacaciones;

import android.app.Notification;
import android.app.Person;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.service.notification.StatusBarNotification;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Guarda (solo en el móvil, en el almacenamiento privado de la app) el contenido de las
 * notificaciones quitadas durante el modo vacaciones, para poder verlas al volver.
 * Se borra al activar de nuevo el modo vacaciones o desde la pantalla del historial.
 */
public final class History {

    public static final class Entry {
        public final String pkg;
        public final String title;
        public final String text;
        public final long time;

        Entry(String pkg, String title, String text, long time) {
            this.pkg = pkg;
            this.title = title;
            this.text = text;
            this.time = time;
        }
    }

    private static final String FILE = "historial.json";
    private static final int MAX = 1500;
    private static List<Entry> cache;

    private History() {}

    private static File file(Context c) {
        return new File(c.getApplicationContext().getFilesDir(), FILE);
    }

    private static List<Entry> load(Context c) {
        if (cache != null) return cache;
        cache = new ArrayList<>();
        File f = file(c);
        if (!f.exists()) return cache;
        try {
            String json = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                cache.add(new Entry(o.optString("p"), o.optString("t"), o.optString("x"), o.optLong("w")));
            }
        } catch (Exception ignored) {
        }
        return cache;
    }

    private static void save(Context c) {
        try {
            JSONArray arr = new JSONArray();
            for (Entry e : cache) {
                JSONObject o = new JSONObject();
                o.put("p", e.pkg);
                o.put("t", e.title);
                o.put("x", e.text);
                o.put("w", e.time);
                arr.put(o);
            }
            File tmp = new File(file(c).getPath() + ".tmp");
            Files.write(tmp.toPath(), arr.toString().getBytes(StandardCharsets.UTF_8));
            if (!tmp.renameTo(file(c))) {
                Files.write(file(c).toPath(), arr.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
        }
    }

    private static String str(CharSequence cs) {
        return cs == null ? "" : cs.toString().trim();
    }

    /**
     * Guarda el contenido de una notificación. Devuelve cuántas entradas nuevas se han añadido
     * (las repetidas no cuentan: muchas apps reenvían la misma notificación con mensajes acumulados).
     */
    public static synchronized int add(Context c, StatusBarNotification sbn) {
        Notification n = sbn.getNotification();
        if (n == null || n.extras == null) return 0;
        Bundle ex = n.extras;
        String pkg = sbn.getPackageName();
        String title = str(ex.getCharSequence(Notification.EXTRA_TITLE));
        List<Entry> found = new ArrayList<>();
        boolean messaging = false; // los mensajes traen su propia hora: sirve para distinguir repetidos

        // Apps de mensajería (WhatsApp, Teams...): cada mensaje por separado
        Parcelable[] msgs = ex.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (msgs != null && msgs.length > 0) {
            messaging = true;
            String conv = str(ex.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE));
            for (Parcelable p : msgs) {
                if (!(p instanceof Bundle)) continue;
                Bundle b = (Bundle) p;
                String text = str(b.getCharSequence("text"));
                if (text.isEmpty()) continue;
                String sender = str(b.getCharSequence("sender"));
                if (sender.isEmpty() && Build.VERSION.SDK_INT >= 28) {
                    Object person = b.getParcelable("sender_person");
                    if (person instanceof Person) sender = str(((Person) person).getName());
                }
                if (sender.isEmpty()) sender = title;
                String t = conv.isEmpty() || conv.equals(sender) ? sender : conv + " · " + sender;
                long when = b.getLong("time", sbn.getPostTime());
                found.add(new Entry(pkg, t, text, when));
            }
        }

        // Resto de apps: título + texto (el largo si lo hay)
        if (found.isEmpty()) {
            messaging = false;
            CharSequence big = ex.getCharSequence(Notification.EXTRA_BIG_TEXT);
            String text = str(big != null && big.length() > 0 ? big : ex.getCharSequence(Notification.EXTRA_TEXT));
            if (text.isEmpty()) text = str(n.tickerText);
            if (title.isEmpty() && text.isEmpty()) return 0;
            found.add(new Entry(pkg, title, text, sbn.getPostTime()));
        }

        List<Entry> list = load(c);
        int added = 0;
        for (Entry e : found) {
            if (isDuplicate(list, e, messaging)) continue;
            list.add(e);
            added++;
        }
        if (added > 0) {
            while (list.size() > MAX) list.remove(0);
            save(c);
        }
        return added;
    }

    private static boolean isDuplicate(List<Entry> list, Entry e, boolean matchTime) {
        for (int i = list.size() - 1, n = 0; i >= 0 && n < 200; i--, n++) {
            Entry o = list.get(i);
            if (o.pkg.equals(e.pkg) && o.title.equals(e.title) && o.text.equals(e.text)
                    && (!matchTime || o.time == e.time)) return true;
        }
        return false;
    }

    public static synchronized List<Entry> getAll(Context c) {
        return new ArrayList<>(load(c));
    }

    public static synchronized boolean isEmpty(Context c) {
        return load(c).isEmpty();
    }

    public static synchronized void clear(Context c) {
        cache = new ArrayList<>();
        //noinspection ResultOfMethodCallIgnored
        file(c).delete();
    }
}
