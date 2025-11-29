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

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LocationService extends Service {
    private static final String TAG = "LocationService";
    private static final String CHANNEL_ID = "BarrierOpenerServiceChannel";
    private static final int NOTIFICATION_ID = 1;

    private static boolean _isStarted = false;

    private LocationManager locationManager;
    private DatabaseHelper dbHelper;
    private Set<Long> recentlyTriggeredBarriers;
    private Handler handler;


    @Override
    public void onCreate() {
        super.onCreate();
        dbHelper = new DatabaseHelper(this);
        recentlyTriggeredBarriers = new HashSet<>();
        handler = new Handler(Looper.getMainLooper());
        locationManager = new LocationManager(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Starting service...");

        _isStarted = true;

        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());
        startLocationUpdates();

        Log.d(TAG, "Service started");

        return START_STICKY;
    }

    public static boolean isStarted() {
        return _isStarted;
    }

    private void createNotificationChannel() {
        NotificationChannel serviceChannel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
        );
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(serviceChannel);
    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .build();
    }

    private void startLocationUpdates() {
        locationManager.startLocationUpdates(new LocationCallback() {
            @Override
            public void onLocationUpdated(Location location) {
                checkNearbyBarriers(location);
            }

            @Override
            public void onLocationError(String error) {
                Log.e(TAG, "Location error: " + error);
            }

            @Override
            public void onProviderStatusChanged(String provider, boolean enabled) {
                Log.d(TAG, "Provider " + provider + " " + (enabled ? "enabled" : "disabled"));
                if (!enabled) {
                    // Try to find another provider
                    locationManager.startLocationUpdates(this);
                }
            }
        });
    }

    private void checkNearbyBarriers(Location location) {
        List<Barrier> barriers = dbHelper.getAllBarriers();
        for (Barrier barrier : barriers) {
            if (!recentlyTriggeredBarriers.contains(barrier.getId()) && isBarrierInRange(barrier, location)) {
                triggerBarrierPopup(barrier);
            }
        }
    }

    private boolean isBarrierInRange(Barrier barrier, Location location) {
        // Calculate distance to barrier
        float[] results = new float[1];
        android.location.Location.distanceBetween(
                location.getLatitude(),
                location.getLongitude(),
                barrier.getLatitude(),
                barrier.getLongitude(),
                results
        );

        float distance = results[0];
        if (distance > barrier.getDetectionRadius()) {
            return false; // Too far away
        }

        // Create a Location object for the barrier
        Location barrierLocation = new Location("barrier");
        barrierLocation.setLatitude(barrier.getLatitude());
        barrierLocation.setLongitude(barrier.getLongitude());

        // Calculate bearing from current location to barrier
        float bearingToBarrier = location.bearingTo(barrierLocation);

        if (location.hasBearing()) {
            float angleDifference = calcAngleDifference(bearingToBarrier, location.getBearing());

            // Check if we're not heading within 45 degrees of the barrier's center
            if (angleDifference > 45) {
                return false;
            }
        }

        if (barrier.getBarrierType() == Barrier.BarrierType.BIDIRECTIONAL) {
            return true; // Bidirectional barrier and within range
        }

        // Translate barrier heading to the same coordinate system as the location bearing (North on top)
        float barrierHeading = ((float) barrier.getHeading() + 270) % 360;

        // For one-way barriers, check if we're approaching with a close direction to a barrier's one
        float angleDifference = calcAngleDifference(location.getBearing(), barrierHeading);

        Log.d(TAG, "Barrier: " + barrier.getName()
                + ", Location bearing: " + location.getBearing()
                + ", barrier heading: " + barrierHeading
                + ", bearing to barrier: " + bearingToBarrier
                + ", angle difference: " + angleDifference);

        // Check if we're approaching within 45 degrees of the barrier's heading
        return angleDifference <= 45;
    }

    private static float calcAngleDifference(float bearing1, float bearing2) {
        float angleDifference = Math.abs(bearing1 - bearing2);
        if (angleDifference > 180) {
            angleDifference = 360 - angleDifference;
        }
        return angleDifference;
    }

    private void triggerBarrierPopup(Barrier barrier) {
        // Add to recently triggered set to prevent repeated triggers
        recentlyTriggeredBarriers.add(barrier.getId());

        // Remove from set after 5 minutes to allow re-triggering
        handler.postDelayed(() -> {
            recentlyTriggeredBarriers.remove(barrier.getId());
        }, 300000);

        // Launch popup activity
        Intent popupIntent = new Intent(this, BarrierPopupActivity.class);
        popupIntent.putExtra("barrier_id", barrier.getId());
        popupIntent.putExtra("barrier_name", barrier.getName());
        popupIntent.putExtra("barrier_phone", barrier.getPhoneNumber());
        popupIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(popupIntent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        locationManager.stopLocationUpdates();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
