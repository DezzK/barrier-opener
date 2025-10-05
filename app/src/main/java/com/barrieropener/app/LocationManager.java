/*
 * Copyright © 2025 Dezz (https://github.com/DezzK)
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
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class LocationManager {
    private static final String TAG = "LocationManager";

    private final Context context;
    private final android.location.LocationManager locationManager;
    private final ScheduledExecutorService executor;
    private LocationCallback locationCallback;
    private boolean isListening = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(@NonNull Location location) {
            Log.d(TAG, "Location updated: " + location);
            if (locationCallback != null) {
                mainHandler.post(() -> locationCallback.onLocationUpdated(location));
            }
        }

        @Override
        public void onProviderEnabled(@NonNull String provider) {
            Log.d(TAG, "Provider enabled: " + provider);
            if (locationCallback != null) {
                mainHandler.post(() -> locationCallback.onProviderStatusChanged(provider, true));
            }
        }

        @Override
        public void onProviderDisabled(@NonNull String provider) {
            Log.d(TAG, "Provider disabled: " + provider);
            if (locationCallback != null) {
                mainHandler.post(() -> locationCallback.onProviderStatusChanged(provider, false));
            }
        }

        @Override
        public void onStatusChanged(String provider, int status, android.os.Bundle extras) {
            Log.d(TAG, String.format("Provider status changed. Provider: %s, status: %d", provider, status));
        }
    };

    public LocationManager(Context context) {
        this.context = context.getApplicationContext();
        this.locationManager = context.getSystemService(android.location.LocationManager.class);
        this.executor = Executors.newSingleThreadScheduledExecutor();
    }

    public void startLocationUpdates(LocationCallback callback) {
        if (isListening)
            return;

        this.locationCallback = callback;
        this.isListening = true;

        if (!hasLocationPermission()) {
            notifyError(context.getString(R.string.location_permission_required));
            return;
        }

        List<String> tmpProviders = locationManager.getAllProviders();
        Log.d(TAG, "Providers: " + tmpProviders);

        Criteria criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_FINE);
        String provider = locationManager.getBestProvider(criteria, true);

        if (provider == null) {
            notifyError(context.getString(R.string.enable_location_services));
            return;
        }

        Log.d(TAG, "Selected provider: " + provider);

        try {
            long minTimeMs = 1000; // 1 second
            float minDistanceM = 1; // 1 meter

            locationManager.requestLocationUpdates(
                    provider,
                    minTimeMs,
                    minDistanceM,
                    locationListener,
                    Looper.getMainLooper()
            );
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException in startLocationUpdates", e);
            notifyError(context.getString(R.string.location_permission_denied));
        }
    }

    public void stopLocationUpdates() {
        if (!isListening) return;

        try {
            locationManager.removeUpdates(locationListener);
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException in stopLocationUpdates", e);
        } finally {
            isListening = false;
        }
    }

    public boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private void notifyError(String error) {
        if (locationCallback != null) {
            mainHandler.post(() -> locationCallback.onLocationError(error));
        }
    }

    public void destroy() {
        stopLocationUpdates();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(100, TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
