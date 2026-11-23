package com.dani.modovacaciones;

import android.Manifest;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.app.AlertDialog;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {

    private TextView txtStatus, txtAppsSummary, txtEndTime, txtExactHint, txtStatsTitle, txtStats;
    private View cardPermission, cardStats;
    private ViewGroup groupsContainer;
    private Button btnToggle, btnClearDate, btnExactPerm, btnHistory;
    private BellView bellView;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            refreshUi();
            handler.postDelayed(this, 15_000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        txtStatus = findViewById(R.id.txtStatus);
        txtAppsSummary = findViewById(R.id.txtAppsSummary);
        txtEndTime = findViewById(R.id.txtEndTime);
        txtExactHint = findViewById(R.id.txtExactHint);
        txtStatsTitle = findViewById(R.id.txtStatsTitle);
        txtStats = findViewById(R.id.txtStats);
        cardPermission = findViewById(R.id.cardPermission);
        cardStats = findViewById(R.id.cardStats);
        btnToggle = findViewById(R.id.btnToggle);
        bellView = findViewById(R.id.bellView);
        btnClearDate = findViewById(R.id.btnClearDate);
        btnExactPerm = findViewById(R.id.btnExactPerm);
        btnHistory = findViewById(R.id.btnHistory);
        groupsContainer = findViewById(R.id.groupsContainer);
        btnHistory.setOnClickListener(v -> startActivity(new Intent(this, HistoryActivity.class)));

        btnToggle.setOnClickListener(v -> toggle());
        findViewById(R.id.btnGrantAccess).setOnClickListener(v -> openListenerSettings());
        findViewById(R.id.btnAppInfo).setOnClickListener(v -> openAppInfo());
        findViewById(R.id.btnNewGroup).setOnClickListener(v -> openGroup(null));
        findViewById(R.id.btnPickDate).setOnClickListener(v -> pickDateTime());
        findViewById(R.id.btnCountdown).setOnClickListener(v -> showCountdownDialog());
        btnClearDate.setOnClickListener(v -> {
            VacationController.setEndTime(this, 0); // también quita la cuenta atrás pendiente
            refreshUi();
        });
        btnExactPerm.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 31) {
                safeStart(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:" + getPackageName())));
            }
        });

        Notifier.ensureChannels(this);
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (VacationController.isEffectivelyActive(this)) {
            Scheduler.schedule(this); // por si ha cambiado el permiso de alarmas exactas
            VacationNotificationListener.cleanNow();
        }
        renderGroups();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(ticker);
    }

    // ---------------------------------------------------------------- acciones

    private void toggle() {
        if (Prefs.isActive(this)) {
            VacationController.deactivate(this, false);
            toast("Modo vacaciones desactivado");
        } else {
            if (!Util.hasListenerAccess(this)) {
                toast("Primero dale acceso a las notificaciones");
                openListenerSettings();
                return;
            }
            if (Prefs.getBlocked(this).isEmpty()) {
                if (Groups.getAll(this).isEmpty()) {
                    toast("Crea un grupo con las apps que quieres silenciar");
                    openGroup(null);
                } else {
                    toast("Marca al menos un grupo que tenga apps");
                }
                return;
            }
            VacationController.activate(this);
            toast("¡Felices vacaciones! 🏖️");
        }
        refreshUi();
    }

    /** Abre el editor de grupos: id == null para crear uno nuevo. */
    private void openGroup(String id) {
        Intent i = new Intent(this, AppPickerActivity.class);
        if (id != null) i.putExtra(AppPickerActivity.EXTRA_GROUP_ID, id);
        startActivity(i);
    }

    /** Lista de grupos con su casilla para marcar cuáles se silencian. */
    private void renderGroups() {
        groupsContainer.removeAllViews();
        LayoutInflater inf = getLayoutInflater();
        for (Groups.Group g : Groups.getAll(this)) {
            View row = inf.inflate(R.layout.item_group, groupsContainer, false);
            ((TextView) row.findViewById(R.id.icon)).setText(g.icon);
            ((TextView) row.findViewById(R.id.name)).setText(g.name);
            ((TextView) row.findViewById(R.id.summary)).setText(groupSummary(g));
            CheckBox check = row.findViewById(R.id.check);
            check.setChecked(g.enabled);
            final String id = g.id;
            check.setOnCheckedChangeListener((b, checked) -> {
                Groups.setEnabled(this, id, checked);
                if (Prefs.isActive(this)) VacationNotificationListener.cleanNow();
                VacationTileService.refresh(this);
                refreshUi();
            });
            row.setOnClickListener(v -> openGroup(id));
            groupsContainer.addView(row);
        }
    }

    private String groupSummary(Groups.Group g) {
        if (g.apps.isEmpty()) return "Sin apps todavía · toca para añadir";
        StringBuilder sb = new StringBuilder(Util.plural(g.apps.size(), "app", "apps")).append(": ");
        int i = 0;
        for (String pkg : g.apps) {
            if (i == 3) break;
            if (i > 0) sb.append(", ");
            sb.append(Util.appLabel(this, pkg));
            i++;
        }
        if (g.apps.size() > 3) sb.append("…");
        return sb.toString();
    }

    private void openListenerSettings() {
        if (Build.VERSION.SDK_INT >= 30) {
            Intent detail = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                            Util.listenerComponent(this).flattenToString());
            if (safeStart(detail)) return;
        }
        safeStart(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
    }

    private void openAppInfo() {
        safeStart(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null)));
    }

    private boolean safeStart(Intent i) {
        try {
            startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static final int[] PRESET_MINUTES = {15, 30, 60, 120, 240, 480, 720, 1440};

    private static String presetLabel(int minutes) {
        return minutes < 60 ? minutes + " min" : (minutes / 60) + " h";
    }

    private void showCountdownDialog() {
        View v = getLayoutInflater().inflate(R.layout.dialog_countdown, null);
        LinearLayout presets = v.findViewById(R.id.presets);
        final NumberPicker pickHours = v.findViewById(R.id.pickHours);
        final NumberPicker pickMinutes = v.findViewById(R.id.pickMinutes);
        pickHours.setMinValue(0);
        pickHours.setMaxValue(99);
        pickHours.setValue(1);
        pickMinutes.setMinValue(0);
        pickMinutes.setMaxValue(59);
        pickMinutes.setValue(0);
        pickMinutes.setFormatter(i -> String.format(Util.ES, "%02d", i));

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Cuenta atrás")
                .setView(v)
                .setPositiveButton("Empezar", (d, w) -> {
                    int minutes = pickHours.getValue() * 60 + pickMinutes.getValue();
                    if (minutes <= 0) {
                        toast("Elige un tiempo mayor que 0");
                        return;
                    }
                    startCountdown(minutes);
                })
                .setNegativeButton("Cancelar", null)
                .create();

        float dp = getResources().getDisplayMetrics().density;
        LinearLayout row = null;
        for (int i = 0; i < PRESET_MINUTES.length; i++) {
            if (i % 4 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (i > 0) rlp.topMargin = (int) (8 * dp);
                presets.addView(row, rlp);
            }
            Button b = new Button(this);
            b.setText(presetLabel(PRESET_MINUTES[i]));
            b.setAllCaps(false);
            b.setBackgroundResource(R.drawable.bg_btn_soft);
            b.setTextColor(getColor(R.color.soft_text));
            b.setStateListAnimator(null);
            b.setMinWidth(0);
            b.setMinimumWidth(0);
            b.setMinHeight((int) (44 * dp));
            b.setMinimumHeight((int) (44 * dp));
            b.setPadding(0, 0, 0, 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i % 4 != 0) lp.setMarginStart((int) (8 * dp));
            final int minutes = PRESET_MINUTES[i];
            b.setOnClickListener(x -> {
                startCountdown(minutes);
                dialog.dismiss();
            });
            row.addView(b, lp);
        }
        dialog.show();
    }

    private void startCountdown(int minutes) {
        long ms = minutes * 60_000L;
        VacationController.setCountdown(this, ms);
        refreshUi();
        if (Prefs.isActive(this)) {
            toast("Se desactivará dentro de " + Util.formatDuration(ms));
        } else {
            toast("La cuenta atrás de " + Util.formatDuration(ms) + " empezará al activarlo");
        }
    }

    private void pickDateTime() {
        final Calendar cal = Calendar.getInstance();
        long end = Prefs.getEndTime(this);
        if (end > System.currentTimeMillis()) {
            cal.setTimeInMillis(end);
        } else {
            cal.add(Calendar.DAY_OF_MONTH, 1);
            cal.set(Calendar.HOUR_OF_DAY, 9);
            cal.set(Calendar.MINUTE, 0);
        }

        DatePickerDialog dp = new DatePickerDialog(this, (view, year, month, day) -> {
            cal.set(Calendar.YEAR, year);
            cal.set(Calendar.MONTH, month);
            cal.set(Calendar.DAY_OF_MONTH, day);
            new TimePickerDialog(this, (tp, hour, minute) -> {
                cal.set(Calendar.HOUR_OF_DAY, hour);
                cal.set(Calendar.MINUTE, minute);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                long t = cal.getTimeInMillis();
                if (t <= System.currentTimeMillis()) {
                    toast("Esa hora ya ha pasado, elige una futura");
                    return;
                }
                VacationController.setEndTime(this, t);
                refreshUi();
                toast("Se desactivará el " + Util.formatDateTime(t));
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
        dp.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        dp.show();
    }

    // ---------------------------------------------------------------- UI

    private void refreshUi() {
        long now = System.currentTimeMillis();
        boolean access = Util.hasListenerAccess(this);
        boolean active = VacationController.isEffectivelyActive(this);
        long end = Prefs.getEndTime(this);
        Set<String> blocked = Prefs.getBlocked(this);

        cardPermission.setVisibility(access ? View.GONE : View.VISIBLE);
        // La primera vez se pinta sin animación; después, cada cambio de estado se anima
        bellView.setSleeping(active, true);

        if (active) {
            txtStatus.setText(end > 0
                    ? "🟢 Activo · se apaga " + Util.formatRemaining(end - now)
                    : "🟢 Activo · hasta que lo apagues");
            btnToggle.setText("Desactivar modo vacaciones");
            btnToggle.setBackgroundResource(R.drawable.bg_btn_sea);
            btnToggle.setTextColor(getColor(R.color.on_accent));
        } else {
            txtStatus.setText("⚪ Apagado · te llegan todas las notificaciones");
            btnToggle.setText("Activar modo vacaciones");
            btnToggle.setBackgroundResource(R.drawable.bg_btn_sun);
            btnToggle.setTextColor(getColor(R.color.on_primary));
        }

        // Grupos
        if (Groups.getAll(this).isEmpty()) {
            txtAppsSummary.setText("Crea grupos (p. ej. «Redes sociales» o «Trabajo») y marca los que quieras silenciar.");
        } else if (blocked.isEmpty()) {
            txtAppsSummary.setText("Marca los grupos que quieres silenciar.");
        } else {
            txtAppsSummary.setText("Se silenciarán " + Util.plural(blocked.size(), "app", "apps") + ".");
        }

        // Fecha de fin
        if (end > 0) {
            txtEndTime.setText("Se apagará solo el " + Util.formatDateTime(end)
                    + " (" + Util.formatRemaining(end - now) + ").");
            btnClearDate.setVisibility(View.VISIBLE);
        } else if (Prefs.getPendingCountdown(this) > 0) {
            txtEndTime.setText("Cuenta atrás de " + Util.formatDuration(Prefs.getPendingCountdown(this))
                    + ": empezará a contar cuando actives el modo vacaciones.");
            btnClearDate.setVisibility(View.VISIBLE);
        } else {
            txtEndTime.setText("Sin fecha: seguirá activo hasta que lo desactives tú.");
            btnClearDate.setVisibility(View.GONE);
        }
        boolean showExact = end > 0 && !Util.canExactAlarms(this);
        txtExactHint.setVisibility(showExact ? View.VISIBLE : View.GONE);
        btnExactPerm.setVisibility(showExact ? View.VISIBLE : View.GONE);

        // Estadísticas + historial
        Map<String, Integer> counts = Prefs.getCounts(this);
        int total = Util.total(counts);
        boolean hasHistory = !History.isEmpty(this);
        if (total > 0 || hasHistory) {
            cardStats.setVisibility(View.VISIBLE);
            txtStatsTitle.setText(active ? "Quitadas hasta ahora" : "Mientras estabas fuera");
            txtStats.setText(Util.plural(total, "notificación", "notificaciones") + "\n"
                    + Util.countsSummary(this, counts, 8));
            btnHistory.setVisibility(hasHistory ? View.VISIBLE : View.GONE);
        } else {
            cardStats.setVisibility(View.GONE);
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
