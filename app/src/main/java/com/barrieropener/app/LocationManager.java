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

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * Wraps {@link android.location.LocationManager} and feeds the freshest fix to a single
 * {@link LocationCallback}. Subscribes to GPS_PROVIDER and NETWORK_PROVIDER simultaneously when
 * available — important on cars without Google Play Services where Fused isn't an option.
 */
public class LocationManager {
    private static final String TAG = "LocationManager";

    private static final long MIN_TIME_BETWEEN_UPDATES_MS = 1000L;
    private static final float MIN_DISTANCE_METERS = 1f;

    /** Newer fix is preferred only if it's at least this much fresher. */
    private static final long FIX_AGE_PREFERENCE_NS = 5_000_000_000L; // 5 s

    private final Context context;
    private final android.location.LocationManager systemManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private LocationCallback locationCallback;
    private boolean isListening = false;
    private Location bestKnownLocation;
    private final List<String> activeProviders = new ArrayList<>();

    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(@NonNull Location location) {
            if (!isBetterThanCurrent(location)) return;
            bestKnownLocation = location;
            if (locationCallback != null) {
                mainHandler.post(() -> locationCallback.onLocationUpdated(location));
            }
        }

        @Override
        public void onProviderEnabled(@NonNull String provider) {
            Log.d(TAG, "Provider enabled: " + provider);
            if (!activeProviders.contains(provider) && isUsefulProvider(provider)) {
                subscribe(provider);
            }
            notifyProviderStatus(provider, true);
        }

        @Override
        public void onProviderDisabled(@NonNull String provider) {
            Log.d(TAG, "Provider disabled: " + provider);
            activeProviders.remove(provider);
            notifyProviderStatus(provider, false);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            // No-op: deprecated callback, real status comes via onProviderEnabled/Disabled.
        }
    };

    public LocationManager(Context context) {
        this.context = context.getApplicationContext();
        this.systemManager = context.getSystemService(android.location.LocationManager.class);
    }

    public void startLocationUpdates(LocationCallback callback) {
        this.locationCallback = callback;
        if (isListening) return;

        if (!hasLocationPermission()) {
            notifyError(context.getString(R.string.location_permission_required));
            return;
        }

        for (String provider : systemManager.getAllProviders()) {
            if (!isUsefulProvider(provider)) continue;
            if (!systemManager.isProviderEnabled(provider)) {
                Log.d(TAG, "Provider " + provider + " disabled at start");
                continue;
            }
            subscribe(provider);
        }

        if (activeProviders.isEmpty()) {
            notifyError(context.getString(R.string.location_service_unavailable));
            return;
        }

        isListening = true;
    }

    @SuppressLint("MissingPermission")
    private void subscribe(String provider) {
        try {
            systemManager.requestLocationUpdates(
                    provider,
                    MIN_TIME_BETWEEN_UPDATES_MS,
                    MIN_DISTANCE_METERS,
                    locationListener,
                    Looper.getMainLooper()
            );
            activeProviders.add(provider);
            Log.d(TAG, "Subscribed to provider: " + provider);
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException subscribing to " + provider, e);
            notifyError(context.getString(R.string.location_permission_denied));
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Provider not available: " + provider, e);
        }
    }

    public void stopLocationUpdates() {
        if (!isListening) return;
        try {
            systemManager.removeUpdates(locationListener);
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException in stopLocationUpdates", e);
        } finally {
            activeProviders.clear();
            isListening = false;
        }
    }

    public boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean isUsefulProvider(String provider) {
        return android.location.LocationManager.GPS_PROVIDER.equals(provider)
                || android.location.LocationManager.NETWORK_PROVIDER.equals(provider);
    }

    /**
     * Picks the better of two fixes. Prefers fresher fixes; if comparable in age, prefers more
     * accurate ones. GPS is preferred over network when both are recent.
     */
    private boolean isBetterThanCurrent(Location candidate) {
        if (bestKnownLocation == null) return true;
        long ageDeltaNs = candidate.getElapsedRealtimeNanos()
                - bestKnownLocation.getElapsedRealtimeNanos();
        if (ageDeltaNs > FIX_AGE_PREFERENCE_NS) return true;
        if (ageDeltaNs < -FIX_AGE_PREFERENCE_NS) return false;

        boolean candidateGps = android.location.LocationManager.GPS_PROVIDER
                .equals(candidate.getProvider());
        boolean currentGps = android.location.LocationManager.GPS_PROVIDER
                .equals(bestKnownLocation.getProvider());
        if (candidateGps && !currentGps) return true;
        if (!candidateGps && currentGps) return false;

        if (candidate.hasAccuracy() && bestKnownLocation.hasAccuracy()) {
            return candidate.getAccuracy() < bestKnownLocation.getAccuracy();
        }
        return ageDeltaNs >= 0;
    }

    private void notifyError(String error) {
        if (locationCallback != null) {
            mainHandler.post(() -> locationCallback.onLocationError(error));
        }
    }

    private void notifyProviderStatus(String provider, boolean enabled) {
        if (locationCallback != null) {
            mainHandler.post(() -> locationCallback.onProviderStatusChanged(provider, enabled));
        }
    }

    public void destroy() {
        stopLocationUpdates();
    }

    /** Time in nanoseconds since the most recent fix; used for diagnostics. */
    public long lastFixAgeMs() {
        if (bestKnownLocation == null) return Long.MAX_VALUE;
        return (SystemClock.elapsedRealtimeNanos() - bestKnownLocation.getElapsedRealtimeNanos())
                / 1_000_000L;
    }
}
