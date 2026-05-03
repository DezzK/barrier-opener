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
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.List;

public class LocationService extends Service {
    private static final String TAG = "LocationService";
    private static final String CHANNEL_ID = "BarrierOpenerServiceChannel";
    private static final String POPUP_CHANNEL_ID = "BarrierPopupChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final int POPUP_NOTIFICATION_ID = 2;
    public static final String ACTION_STOP = "com.barrieropener.app.STOP";

    private static volatile boolean _isStarted = false;

    private LocationManager locationManager;
    private BarrierRepository repository;
    private TriggerStateStore triggerState;

    @Override
    public void onCreate() {
        super.onCreate();
        repository = new BarrierRepository(this);
        repository.registerInvalidator();
        triggerState = new TriggerStateStore(this);
        locationManager = new LocationManager(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            Log.d(TAG, "Stop action received");
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        Log.d(TAG, "Starting service...");
        _isStarted = true;

        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());
        startLocationUpdates();

        return START_STICKY;
    }

    public static boolean isStarted() {
        return _isStarted;
    }

    private void createNotificationChannel() {
        NotificationManager manager = getSystemService(NotificationManager.class);

        NotificationChannel serviceChannel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
        );
        manager.createNotificationChannel(serviceChannel);

        NotificationChannel popupChannel = new NotificationChannel(
                POPUP_CHANNEL_ID,
                getString(R.string.notification_popup_channel_name),
                NotificationManager.IMPORTANCE_HIGH
        );
        popupChannel.enableVibration(true);
        popupChannel.setBypassDnd(true);
        manager.createNotificationChannel(popupChannel);
    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent contentPi = PendingIntent.getActivity(
                this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        Intent stopIntent = new Intent(this, LocationService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(
                this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(contentPi)
                .addAction(0, getString(R.string.notification_action_stop), stopPi)
                .setOngoing(true)
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
            }
        });
    }

    private void checkNearbyBarriers(Location location) {
        List<Barrier> barriers = repository.getAll();
        triggerState.releaseFarBarriers(location.getLatitude(), location.getLongitude(), barriers);

        for (Barrier barrier : barriers) {
            if (triggerState.isTriggered(barrier.getId())) continue;
            BarrierDetector.Result result = BarrierDetector.evaluate(barrier, location);
            if (result == BarrierDetector.Result.IN_RANGE) {
                triggerBarrierPopup(barrier);
            }
        }
    }

    private void triggerBarrierPopup(Barrier barrier) {
        triggerState.markTriggered(barrier.getId());

        Intent popupIntent = new Intent(this, BarrierPopupActivity.class);
        popupIntent.putExtra("barrier_id", barrier.getId());
        popupIntent.putExtra("barrier_name", barrier.getName());
        popupIntent.putExtra("barrier_phone", barrier.getPhoneNumber());
        popupIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        // Try to launch the popup activity directly. On Android 10+ background-activity launch is
        // restricted, so we always also post a high-priority notification with a full-screen intent
        // — the system will surface that on the lock screen / over other apps as a fallback.
        try {
            startActivity(popupIntent);
        } catch (Exception e) {
            Log.w(TAG, "Could not start popup activity directly", e);
        }
        showPopupFullScreenNotification(barrier, popupIntent);
    }

    private void showPopupFullScreenNotification(Barrier barrier, Intent popupIntent) {
        PendingIntent fullScreenPi = PendingIntent.getActivity(
                this, (int) barrier.getId(), popupIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification n = new NotificationCompat.Builder(this, POPUP_CHANNEL_ID)
                .setContentTitle(getString(R.string.popup_title_window, barrier.getName()))
                .setContentText(barrier.getPhoneNumber())
                .setSmallIcon(R.drawable.ic_notification)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setFullScreenIntent(fullScreenPi, true)
                .setContentIntent(fullScreenPi)
                .setAutoCancel(true)
                .setTimeoutAfter(15_000L)
                .build();

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(POPUP_NOTIFICATION_ID, n);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        _isStarted = false;
        locationManager.stopLocationUpdates();
        repository.unregisterInvalidator();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
