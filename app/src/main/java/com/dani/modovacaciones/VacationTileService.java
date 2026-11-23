package com.dani.modovacaciones;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Mosaico "Vacaciones" en los ajustes rápidos. */
public class VacationTileService extends TileService {

    public static void refresh(Context c) {
        try {
            TileService.requestListeningState(c, new ComponentName(c, VacationTileService.class));
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        update();
    }

    @Override
    public void onClick() {
        super.onClick();
        if (Prefs.isActive(this)) {
            VacationController.deactivate(this, false);
        } else if (!Util.hasListenerAccess(this) || Prefs.getBlocked(this).isEmpty()) {
            openApp();
            return;
        } else {
            VacationController.activate(this);
        }
        update();
    }

    @SuppressWarnings("deprecation")
    private void openApp() {
        Intent i = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 3, i, PendingIntent.FLAG_IMMUTABLE));
        } else {
            startActivityAndCollapse(i);
        }
    }

    private void update() {
        Tile tile = getQsTile();
        if (tile == null) return;
        boolean active = VacationController.isEffectivelyActive(this);
        tile.setState(active ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setLabel("Vacaciones");
        if (Build.VERSION.SDK_INT >= 29) {
            long end = Prefs.getEndTime(this);
            String sub;
            if (!active) sub = "Apagado";
            else if (end > 0) sub = "Hasta " + Util.formatShort(end);
            else sub = "Activo";
            tile.setSubtitle(sub);
        }
        tile.updateTile();
    }
}
