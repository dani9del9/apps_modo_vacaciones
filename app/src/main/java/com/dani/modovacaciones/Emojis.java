package com.dani.modovacaciones;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Catálogo de emojis para el icono de los grupos (el mismo que usa YTBox):
 * assets/emoji-data.json → [{key, label, items: [[emoji, nombre en inglés], ...]}, ...]
 */
public final class Emojis {

    public static final class Item {
        public final String emoji;
        public final String name;

        Item(String emoji, String name) {
            this.emoji = emoji;
            this.name = name;
        }
    }

    public static final class Category {
        public final String key;
        public final String label;
        public final List<Item> items;

        Category(String key, String label, List<Item> items) {
            this.key = key;
            this.label = label;
            this.items = items;
        }
    }

    private static List<Category> cache;

    private Emojis() {}

    public static synchronized List<Category> load(Context c) {
        if (cache != null) return cache;
        List<Category> out = new ArrayList<>();
        try (InputStream in = c.getAssets().open("emoji-data.json")) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] b = new byte[16384];
            int n;
            while ((n = in.read(b)) > 0) buf.write(b, 0, n);
            JSONArray arr = new JSONArray(new String(buf.toByteArray(), StandardCharsets.UTF_8));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                JSONArray items = o.getJSONArray("items");
                List<Item> list = new ArrayList<>(items.length());
                for (int j = 0; j < items.length(); j++) {
                    JSONArray it = items.getJSONArray(j);
                    list.add(new Item(it.getString(0), it.optString(1, "")));
                }
                out.add(new Category(o.getString("key"), o.getString("label"), list));
            }
        } catch (Exception ignored) {
        }
        cache = Collections.unmodifiableList(out);
        return cache;
    }

    public static int total(List<Category> cats) {
        int n = 0;
        for (Category c : cats) n += c.items.size();
        return n;
    }

    /**
     * Igual que en YTBox: si hay texto de búsqueda se busca en todas las categorías
     * (por nombre en inglés, o el propio emoji); si no, se muestra la categoría elegida
     * (catKey == null = todas).
     */
    public static List<Item> filter(List<Category> cats, String catKey, String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Item> out = new ArrayList<>();
        for (Category c : cats) {
            if (q.isEmpty()) {
                if (catKey == null || catKey.equals(c.key)) out.addAll(c.items);
            } else {
                for (Item it : c.items) {
                    if (it.name.contains(q) || it.emoji.equals(q)) out.add(it);
                }
            }
        }
        return out;
    }
}
