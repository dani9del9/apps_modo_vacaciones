package com.dani.modovacaciones;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.text.Collator;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Lista de todas las apps instaladas con casilla para marcar las que se silencian. */
public class AppPickerActivity extends Activity {

    private static final class AppEntry {
        final String pkg;
        final String label;
        final String search;
        final Drawable icon;

        AppEntry(String pkg, String label, Drawable icon) {
            this.pkg = pkg;
            this.label = label;
            this.icon = icon;
            this.search = normalize(label + " " + pkg);
        }
    }

    private final List<AppEntry> all = new ArrayList<>();
    private final List<AppEntry> shown = new ArrayList<>();
    private Set<String> selected;

    private AppAdapter adapter;
    private TextView txtCount;
    private EditText edtSearch;
    private ProgressBar progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_picker);

        selected = Prefs.getBlocked(this);
        txtCount = findViewById(R.id.txtCount);
        edtSearch = findViewById(R.id.edtSearch);
        progress = findViewById(R.id.progress);
        ListView list = findViewById(R.id.list);

        adapter = new AppAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            AppEntry e = shown.get(position);
            if (!selected.remove(e.pkg)) selected.add(e.pkg);
            save();
            adapter.notifyDataSetChanged();
        });

        edtSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { applyFilter(); }
        });

        findViewById(R.id.btnAll).setOnClickListener(v -> {
            for (AppEntry e : shown) selected.add(e.pkg);
            save();
            adapter.notifyDataSetChanged();
        });
        findViewById(R.id.btnNone).setOnClickListener(v -> {
            for (AppEntry e : shown) selected.remove(e.pkg);
            save();
            adapter.notifyDataSetChanged();
        });
        findViewById(R.id.btnDone).setOnClickListener(v -> finish());
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        updateCount();
        loadApps();
    }

    private void save() {
        Prefs.setBlocked(this, selected);
        updateCount();
        if (Prefs.isActive(this)) {
            Notifier.showActive(this);
            VacationNotificationListener.cleanNow();
        }
    }

    private void updateCount() {
        txtCount.setText(Util.plural(selected.size(), "marcada", "marcadas"));
    }

    private void loadApps() {
        progress.setVisibility(View.VISIBLE);
        final Set<String> initiallySelected = new HashSet<>(selected);
        final String myPkg = getPackageName();
        final PackageManager pm = getPackageManager();

        new Thread(() -> {
            Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> infos = pm.queryIntentActivities(launcher, 0);
            Map<String, AppEntry> byPkg = new LinkedHashMap<>();
            for (ResolveInfo ri : infos) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(myPkg) || byPkg.containsKey(pkg)) continue;
                String label;
                Drawable icon;
                try {
                    ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                    label = pm.getApplicationLabel(ai).toString();
                    icon = pm.getApplicationIcon(ai);
                } catch (Exception ex) {
                    label = ri.loadLabel(pm).toString();
                    icon = ri.loadIcon(pm);
                }
                byPkg.put(pkg, new AppEntry(pkg, label, icon));
            }

            final List<AppEntry> result = new ArrayList<>(byPkg.values());
            final Collator collator = Collator.getInstance(Util.ES);
            // Las ya marcadas primero, luego alfabético
            Collections.sort(result, (a, b) -> {
                boolean sa = initiallySelected.contains(a.pkg);
                boolean sb = initiallySelected.contains(b.pkg);
                if (sa != sb) return sa ? -1 : 1;
                return collator.compare(a.label, b.label);
            });

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                all.clear();
                all.addAll(result);
                progress.setVisibility(View.GONE);
                applyFilter();
            });
        }).start();
    }

    private void applyFilter() {
        String q = normalize(edtSearch.getText().toString().trim());
        shown.clear();
        for (AppEntry e : all) {
            if (q.isEmpty() || e.search.contains(q)) shown.add(e);
        }
        adapter.notifyDataSetChanged();
    }

    private static String normalize(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD);
        return n.replaceAll("\\p{M}", "").toLowerCase(Util.ES);
    }

    private static final class Holder {
        ImageView icon;
        TextView label;
        TextView pkg;
        CheckBox check;
    }

    private final class AppAdapter extends BaseAdapter {
        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int position) { return shown.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Holder h;
            if (convertView == null) {
                convertView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
                h = new Holder();
                h.icon = convertView.findViewById(R.id.icon);
                h.label = convertView.findViewById(R.id.label);
                h.pkg = convertView.findViewById(R.id.pkg);
                h.check = convertView.findViewById(R.id.check);
                convertView.setTag(h);
            } else {
                h = (Holder) convertView.getTag();
            }
            AppEntry e = shown.get(position);
            h.icon.setImageDrawable(e.icon);
            h.label.setText(e.label);
            h.pkg.setText(e.pkg);
            h.check.setChecked(selected.contains(e.pkg));
            return convertView;
        }
    }
}
