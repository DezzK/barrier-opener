/*
 * Copyright © 2025-2026 Dezz (https://github.com/DezzK)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.barrieropener.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.Map;

/**
 * Tracks which barriers have already been triggered to avoid showing the popup repeatedly while
 * the car sits in the detection zone. Persisted in SharedPreferences so the state survives a
 * service restart.
 *
 * State machine: a barrier becomes TRIGGERED when the popup is shown and is cleared when the car
 * leaves a "release radius" (twice the detection radius) or after {@link #FALLBACK_TTL_MS}
 * elapses (safety net for stuck flags).
 */
public class TriggerStateStore {
    private static final String PREFS = "trigger_state";
    private static final long FALLBACK_TTL_MS = 24L * 60 * 60 * 1000;
    private static final float RELEASE_RADIUS_FACTOR = 2.0f;

    private final SharedPreferences prefs;

    public TriggerStateStore(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean isTriggered(long barrierId) {
        long ts = prefs.getLong(key(barrierId), 0L);
        if (ts == 0L) return false;
        if (System.currentTimeMillis() - ts > FALLBACK_TTL_MS) {
            release(barrierId);
            return false;
        }
        return true;
    }

    public void markTriggered(long barrierId) {
        prefs.edit().putLong(key(barrierId), System.currentTimeMillis()).apply();
    }

    public void release(long barrierId) {
        prefs.edit().remove(key(barrierId)).apply();
    }

    public void releaseAll() {
        prefs.edit().clear().apply();
    }

    /**
     * Removes triggered flags for any barrier the car has driven away from beyond the release
     * radius. Should be called on every location update.
     */
    public void releaseFarBarriers(double currentLat, double currentLon,
                                   Iterable<Barrier> barriers) {
        Map<String, ?> all = new HashMap<>(prefs.getAll());
        if (all.isEmpty()) return;
        SharedPreferences.Editor editor = null;
        for (Barrier b : barriers) {
            String k = key(b.getId());
            if (!all.containsKey(k)) continue;
            float[] dist = new float[1];
            android.location.Location.distanceBetween(
                    currentLat, currentLon,
                    b.getLatitude(), b.getLongitude(),
                    dist);
            if (dist[0] > b.getDetectionRadius() * RELEASE_RADIUS_FACTOR) {
                if (editor == null) editor = prefs.edit();
                editor.remove(k);
            }
        }
        if (editor != null) editor.apply();
    }

    private static String key(long id) {
        return "b_" + id;
    }
}
