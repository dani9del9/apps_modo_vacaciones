package com.dani.modovacaciones;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** "Mientras estabas fuera": notificaciones quitadas, agrupadas por app. */
public class HistoryActivity extends Activity {

    /** Fila de la lista: o cabecera de app (entry == null) o una notificación. */
    private static final class Row {
        final String pkg;
        final int count;
        final History.Entry entry;

        Row(String pkg, int count, History.Entry entry) {
            this.pkg = pkg;
            this.count = count;
            this.entry = entry;
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private final Map<String, String> labels = new HashMap<>();
    private final Map<String, Drawable> icons = new HashMap<>();
    private RowAdapter adapter;
    private TextView txtCount, txtEmpty;
    private View btnClear;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        txtCount = findViewById(R.id.txtCount);
        txtEmpty = findViewById(R.id.txtEmpty);
        btnClear = findViewById(R.id.btnClear);
        ListView list = findViewById(R.id.list);
        adapter = new RowAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> openApp(rows.get(position).pkg));

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        btnClear.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Borrar historial")
                .setMessage("Se borrarán todas las notificaciones guardadas.")
                .setPositiveButton("Borrar", (d, w) -> {
                    History.clear(this);
                    Prefs.clearCounts(this);
                    load();
                })
                .setNegativeButton("Cancelar", null)
                .show());

        load();
    }

    private void load() {
        List<History.Entry> all = History.getAll(this);
        Map<String, List<History.Entry>> byApp = new LinkedHashMap<>();
        for (History.Entry e : all) {
            List<History.Entry> l = byApp.get(e.pkg);
            if (l == null) {
                l = new ArrayList<>();
                byApp.put(e.pkg, l);
            }
            l.add(e);
        }
        List<Map.Entry<String, List<History.Entry>>> groups = new ArrayList<>(byApp.entrySet());
        Collections.sort(groups, (a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()));

        rows.clear();
        for (Map.Entry<String, List<History.Entry>> g : groups) {
            List<History.Entry> entries = g.getValue();
            Collections.sort(entries, (a, b) -> Long.compare(b.time, a.time)); // más recientes arriba
            rows.add(new Row(g.getKey(), entries.size(), null));
            for (History.Entry e : entries) rows.add(new Row(g.getKey(), 0, e));
        }
        adapter.notifyDataSetChanged();

        txtCount.setText(Util.plural(all.size(), "notificación", "notificaciones"));
        txtEmpty.setVisibility(all.isEmpty() ? View.VISIBLE : View.GONE);
        btnClear.setVisibility(all.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void openApp(String pkg) {
        Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
        if (i != null) {
            try {
                startActivity(i);
            } catch (Exception ignored) {
            }
        }
    }

    private String label(String pkg) {
        String l = labels.get(pkg);
        if (l == null) {
            l = Util.appLabel(this, pkg);
            labels.put(pkg, l);
        }
        return l;
    }

    private Drawable icon(String pkg) {
        if (!icons.containsKey(pkg)) {
            Drawable d = null;
            try {
                d = getPackageManager().getApplicationIcon(pkg);
            } catch (PackageManager.NameNotFoundException ignored) {
            }
            icons.put(pkg, d);
        }
        return icons.get(pkg);
    }

    private static String formatTime(long t) {
        if (DateUtils.isToday(t)) return new SimpleDateFormat("HH:mm", Util.ES).format(new Date(t));
        return new SimpleDateFormat("EEE d MMM, HH:mm", Util.ES).format(new Date(t));
    }

    private final class RowAdapter extends BaseAdapter {
        @Override public int getCount() { return rows.size(); }
        @Override public Object getItem(int position) { return rows.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public int getViewTypeCount() { return 2; }
        @Override public int getItemViewType(int position) { return rows.get(position).entry == null ? 0 : 1; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Row r = rows.get(position);
            LayoutInflater inf = LayoutInflater.from(parent.getContext());
            if (r.entry == null) {
                if (convertView == null) convertView = inf.inflate(R.layout.item_history_app, parent, false);
                ImageView ic = convertView.findViewById(R.id.icon);
                ic.setImageDrawable(icon(r.pkg));
                ((TextView) convertView.findViewById(R.id.label)).setText(label(r.pkg));
                ((TextView) convertView.findViewById(R.id.count)).setText(String.valueOf(r.count));
            } else {
                if (convertView == null) convertView = inf.inflate(R.layout.item_history_entry, parent, false);
                TextView title = convertView.findViewById(R.id.title);
                TextView text = convertView.findViewById(R.id.text);
                title.setText(r.entry.title.isEmpty() ? label(r.pkg) : r.entry.title);
                text.setText(r.entry.text);
                text.setVisibility(r.entry.text.isEmpty() ? View.GONE : View.VISIBLE);
                ((TextView) convertView.findViewById(R.id.time)).setText(formatTime(r.entry.time));
            }
            return convertView;
        }
    }
}
