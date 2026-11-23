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
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {

    private TextView txtStatus, txtAppsSummary, txtEndTime, txtExactHint, txtStatsTitle, txtStats;
    private View cardPermission, cardStats;
    private Button btnToggle, btnClearDate, btnExactPerm;
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

        btnToggle.setOnClickListener(v -> toggle());
        findViewById(R.id.btnGrantAccess).setOnClickListener(v -> openListenerSettings());
        findViewById(R.id.btnAppInfo).setOnClickListener(v -> openAppInfo());
        findViewById(R.id.btnChooseApps).setOnClickListener(v -> openPicker());
        findViewById(R.id.btnPickDate).setOnClickListener(v -> pickDateTime());
        btnClearDate.setOnClickListener(v -> {
            VacationController.setEndTime(this, 0);
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
                toast("Elige al menos una app para silenciar");
                openPicker();
                return;
            }
            VacationController.activate(this);
            toast("¡Felices vacaciones! 🏖️");
        }
        refreshUi();
    }

    private void openPicker() {
        startActivity(new Intent(this, AppPickerActivity.class));
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

        // Apps
        if (blocked.isEmpty()) {
            txtAppsSummary.setText("Todavía no has elegido ninguna app.");
        } else {
            List<String> names = new ArrayList<>();
            for (String pkg : blocked) names.add(Util.appLabel(this, pkg));
            Collections.sort(names, Collator.getInstance(Util.ES));
            int max = 6;
            StringBuilder sb = new StringBuilder(Util.plural(blocked.size(), "app", "apps")).append(": ");
            for (int i = 0; i < names.size() && i < max; i++) {
                if (i > 0) sb.append(", ");
                sb.append(names.get(i));
            }
            if (names.size() > max) sb.append(" y ").append(names.size() - max).append(" más");
            txtAppsSummary.setText(sb.toString());
        }

        // Fecha de fin
        if (end > 0) {
            txtEndTime.setText("Se apagará solo el " + Util.formatDateTime(end)
                    + " (" + Util.formatRemaining(end - now) + ").");
            btnClearDate.setVisibility(View.VISIBLE);
        } else {
            txtEndTime.setText("Sin fecha: seguirá activo hasta que lo desactives tú.");
            btnClearDate.setVisibility(View.GONE);
        }
        boolean showExact = end > 0 && !Util.canExactAlarms(this);
        txtExactHint.setVisibility(showExact ? View.VISIBLE : View.GONE);
        btnExactPerm.setVisibility(showExact ? View.VISIBLE : View.GONE);

        // Estadísticas
        Map<String, Integer> counts = Prefs.getCounts(this);
        int total = Util.total(counts);
        if (total > 0) {
            cardStats.setVisibility(View.VISIBLE);
            txtStatsTitle.setText(active ? "🧹 Quitadas hasta ahora" : "🧹 La última vez se quitaron");
            txtStats.setText(Util.plural(total, "notificación", "notificaciones") + "\n"
                    + Util.countsSummary(this, counts, 8));
        } else {
            cardStats.setVisibility(View.GONE);
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
