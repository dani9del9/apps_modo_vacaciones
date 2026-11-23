package com.dani.modovacaciones;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.icu.text.BreakIterator;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.text.Collator;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Crear o editar un grupo: nombre, icono y apps que lo forman. */
public class AppPickerActivity extends Activity {

    public static final String EXTRA_GROUP_ID = "group_id";

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
    private final Set<String> selected = new HashSet<>();

    private Groups.Group group;
    private boolean editing;

    private AppAdapter adapter;
    private TextView txtCount, btnIcon;
    private EditText edtName, edtSearch;
    private ProgressBar progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_picker);

        group = Groups.get(this, getIntent().getStringExtra(EXTRA_GROUP_ID));
        editing = group != null;
        if (group == null) {
            group = new Groups.Group();
            group.id = Groups.newId();
            group.name = "";
            group.icon = Groups.DEFAULT_ICON;
            group.enabled = true;
        }
        selected.addAll(group.apps);

        txtCount = findViewById(R.id.txtCount);
        btnIcon = findViewById(R.id.btnIcon);
        edtName = findViewById(R.id.edtName);
        edtSearch = findViewById(R.id.edtSearch);
        progress = findViewById(R.id.progress);
        ListView list = findViewById(R.id.list);

        ((TextView) findViewById(R.id.txtTitle)).setText(editing ? "Editar grupo" : "Nuevo grupo");
        edtName.setText(group.name);
        btnIcon.setText(group.icon);

        View btnDelete = findViewById(R.id.btnDelete);
        btnDelete.setVisibility(editing ? View.VISIBLE : View.GONE);
        btnDelete.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Borrar grupo")
                .setMessage("¿Borrar el grupo «" + group.name + "»? Las apps no se tocan.")
                .setPositiveButton("Borrar", (d, w) -> {
                    Groups.delete(this, group.id);
                    afterChange();
                    finish();
                })
                .setNegativeButton("Cancelar", null)
                .show());

        btnIcon.setOnClickListener(v -> showIconPicker());

        adapter = new AppAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            AppEntry e = shown.get(position);
            if (!selected.remove(e.pkg)) selected.add(e.pkg);
            adapter.notifyDataSetChanged();
            updateCount();
        });

        edtSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { applyFilter(); }
        });

        findViewById(R.id.btnDone).setOnClickListener(v -> saveAndFinish());
        findViewById(R.id.btnBack).setOnClickListener(v -> saveAndFinish());

        updateCount();
        loadApps();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        saveAndFinish();
    }

    /** Guarda y cierra. Un grupo nuevo sin nombre ni apps se descarta sin preguntar. */
    private void saveAndFinish() {
        String name = edtName.getText().toString().trim();
        if (name.isEmpty()) {
            if (!editing && selected.isEmpty()) {
                finish();
                return;
            }
            edtName.setError("Ponle un nombre al grupo");
            edtName.requestFocus();
            return;
        }
        group.name = name;
        group.apps.clear();
        group.apps.addAll(selected);
        Groups.put(this, group);
        afterChange();
        finish();
    }

    private void afterChange() {
        if (Prefs.isActive(this)) VacationNotificationListener.cleanNow();
        VacationTileService.refresh(this);
    }

    private void showIconPicker() {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_icon_picker, null);
        final GridView grid = v.findViewById(R.id.grid);
        final EditText custom = v.findViewById(R.id.edtCustom);
        final LinearLayout cats = v.findViewById(R.id.categories);
        final ArrayAdapter<String> emojiAdapter = new ArrayAdapter<>(this, R.layout.item_emoji,
                new ArrayList<>(Arrays.asList(Emojis.CATEGORIES[0])));
        grid.setAdapter(emojiAdapter);

        final List<TextView> chips = new ArrayList<>();
        float dp = getResources().getDisplayMetrics().density;
        for (int i = 0; i < Emojis.CATEGORY_NAMES.length; i++) {
            TextView chip = new TextView(this);
            chip.setText(Emojis.CATEGORY_NAMES[i]);
            chip.setTextSize(14);
            chip.setPadding((int) (14 * dp), (int) (8 * dp), (int) (14 * dp), (int) (8 * dp));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd((int) (6 * dp));
            final int idx = i;
            chip.setOnClickListener(c -> {
                emojiAdapter.clear();
                emojiAdapter.addAll(Emojis.CATEGORIES[idx]);
                grid.setSelection(0);
                styleChips(chips, idx);
            });
            chips.add(chip);
            cats.addView(chip, lp);
        }
        styleChips(chips, 0);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Icono del grupo")
                .setView(v)
                .setPositiveButton("Usar el escrito", (d, w) -> {
                    String t = firstGrapheme(custom.getText().toString().trim());
                    if (!t.isEmpty()) setIcon(t);
                })
                .setNegativeButton("Cancelar", null)
                .create();
        grid.setOnItemClickListener((parent, view, position, id) -> {
            setIcon(emojiAdapter.getItem(position));
            dialog.dismiss();
        });
        dialog.show();
    }

    private void styleChips(List<TextView> chips, int selectedIdx) {
        for (int i = 0; i < chips.size(); i++) {
            TextView c = chips.get(i);
            boolean on = i == selectedIdx;
            c.setBackgroundResource(on ? R.drawable.bg_btn_sun : R.drawable.bg_btn_soft);
            c.setTextColor(getColor(on ? R.color.on_primary : R.color.soft_text));
            c.setTypeface(null, on ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    private void setIcon(String icon) {
        group.icon = icon;
        btnIcon.setText(icon);
    }

    /** Primer "carácter visible" (un emoji compuesto cuenta como uno). */
    private static String firstGrapheme(String s) {
        if (s.isEmpty()) return s;
        BreakIterator it = BreakIterator.getCharacterInstance();
        it.setText(s);
        int end = it.next();
        return end == BreakIterator.DONE ? s : s.substring(0, end);
    }

    private void updateCount() {
        txtCount.setText(Util.plural(selected.size(), "app", "apps"));
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
            // Las del grupo primero, luego alfabético
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
