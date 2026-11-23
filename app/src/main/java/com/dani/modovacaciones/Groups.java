package com.dani.modovacaciones;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Grupos de apps (p. ej. "Redes sociales"): nombre, icono (emoji), apps y si están marcados.
 * Las apps silenciadas son la unión de las apps de los grupos marcados.
 */
public final class Groups {

    public static final class Group {
        public String id;
        public String name;
        public String icon;
        public boolean enabled;
        public final Set<String> apps = new LinkedHashSet<>();

        Group copy() {
            Group g = new Group();
            g.id = id;
            g.name = name;
            g.icon = icon;
            g.enabled = enabled;
            g.apps.addAll(apps);
            return g;
        }
    }

    public static final String DEFAULT_ICON = "💬";

    private static final String FILE = "modo_vacaciones";
    private static final String KEY = "groups";
    private static List<Group> cache;

    private Groups() {}

    private static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static String newId() {
        return UUID.randomUUID().toString();
    }

    private static List<Group> load(Context c) {
        if (cache != null) return cache;
        cache = new ArrayList<>();
        String json = p(c).getString(KEY, null);
        if (json == null) {
            // Primera vez con grupos: las apps elegidas en la versión anterior pasan a un grupo
            Set<String> old = Prefs.getLegacyBlocked(c);
            if (!old.isEmpty()) {
                Group g = new Group();
                g.id = newId();
                g.name = "Mis apps";
                g.icon = "⭐";
                g.enabled = true;
                g.apps.addAll(old);
                cache.add(g);
            }
            save(c);
            return cache;
        }
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Group g = new Group();
                g.id = o.optString("id", newId());
                g.name = o.optString("name", "Grupo");
                g.icon = o.optString("icon", DEFAULT_ICON);
                g.enabled = o.optBoolean("enabled", true);
                JSONArray apps = o.optJSONArray("apps");
                if (apps != null) for (int j = 0; j < apps.length(); j++) g.apps.add(apps.getString(j));
                cache.add(g);
            }
        } catch (Exception ignored) {
        }
        return cache;
    }

    private static void save(Context c) {
        try {
            JSONArray arr = new JSONArray();
            for (Group g : cache) {
                JSONObject o = new JSONObject();
                o.put("id", g.id);
                o.put("name", g.name);
                o.put("icon", g.icon);
                o.put("enabled", g.enabled);
                JSONArray apps = new JSONArray();
                for (String a : g.apps) apps.put(a);
                o.put("apps", apps);
                arr.put(o);
            }
            p(c).edit().putString(KEY, arr.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    /** Copia de todos los grupos, en el orden en que se crearon. */
    public static synchronized List<Group> getAll(Context c) {
        List<Group> out = new ArrayList<>();
        for (Group g : load(c)) out.add(g.copy());
        return out;
    }

    public static synchronized Group get(Context c, String id) {
        if (id == null) return null;
        for (Group g : load(c)) if (g.id.equals(id)) return g.copy();
        return null;
    }

    /** Crea o actualiza el grupo (por id). */
    public static synchronized void put(Context c, Group group) {
        List<Group> list = load(c);
        Group copy = group.copy();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(group.id)) {
                list.set(i, copy);
                save(c);
                return;
            }
        }
        list.add(copy);
        save(c);
    }

    public static synchronized void delete(Context c, String id) {
        List<Group> list = load(c);
        for (int i = list.size() - 1; i >= 0; i--) if (list.get(i).id.equals(id)) list.remove(i);
        save(c);
    }

    public static synchronized void setEnabled(Context c, String id, boolean enabled) {
        for (Group g : load(c)) if (g.id.equals(id)) g.enabled = enabled;
        save(c);
    }

    /** Apps que se silencian: todas las de los grupos marcados. */
    public static synchronized Set<String> blockedApps(Context c) {
        Set<String> out = new LinkedHashSet<>();
        for (Group g : load(c)) if (g.enabled) out.addAll(g.apps);
        return out;
    }
}
