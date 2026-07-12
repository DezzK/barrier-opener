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

import android.app.KeyguardManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;

import java.util.ArrayList;
import java.util.List;

public class BarrierPopupActivity extends AppCompatActivity {
    private static final String TAG = "BarrierPopupActivity";
    private static final long AUTO_DISMISS_DELAY_MS = 10_000L;
    private static final long COUNTDOWN_TICK_MS = 1_000L;

    private SoundPool soundPool;
    private int popupSoundId;
    private boolean soundLoaded;
    private boolean shouldPlaySoundWhenLoaded;

    private TextView barrierNameText;
    private TextView autoDismissText;
    private Button btnWait;
    private CountDownTimer countdown;

    private String barrierName;
    private String phoneNumber;
    private long primaryId = -1;
    private String[] nearbyNames;
    private String[] nearbyPhones;

    /** Snapshot of all barriers, loaded once; used to recompute the in-zone list live. */
    private List<Barrier> allBarriers;
    private LocationManager popupLocationManager;
    private String lastNearbySignature;

    private final LocationCallback popupLocationCallback = new LocationCallback() {
        @Override
        public void onLocationUpdated(Location location) {
            recomputeNearby(location);
        }

        @Override
        public void onLocationError(String error) {
            // Keep whatever list we already have.
        }

        @Override
        public void onProviderStatusChanged(String provider, boolean enabled) {
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showOverLockscreen();
        setContentView(R.layout.activity_barrier_popup);

        initViews();
        initSoundPool();
        bindFromIntent();
        startLocationTracking();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // singleInstance activity: a fresh barrier (new popup, or a tap on the full-screen
        // notification) is delivered here, not via onCreate. Re-bind so the UI reflects it.
        setIntent(intent);
        bindFromIntent();
    }

    private void bindFromIntent() {
        long previousPrimaryId = primaryId;
        getIntentData();
        boolean primaryChanged = primaryId != previousPrimaryId;

        updateUI();
        // Force a re-render of the nearby list for this (possibly new) primary barrier.
        lastNearbySignature = null;
        buildNearbyList();

        // Only re-alert (reset the Wait button, restart the countdown, replay the sound) when the
        // suggested barrier actually changed. A same-primary redelivery must NOT undo a "Wait" the
        // user pressed or restart the auto-dismiss out from under them.
        if (primaryChanged) {
            resetWaitButton();
            restartCountdown();
            playSound();
        }

        // The popup is now on screen, so the heads-up notification that backed the full-screen
        // intent is redundant — clear it so it doesn't linger in the shade after we're done.
        cancelPopupNotification();
    }

    private void cancelPopupNotification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.cancel(LocationService.POPUP_NOTIFICATION_ID);
        }
    }

    private void resetWaitButton() {
        btnWait.setEnabled(true);
        btnWait.setBackground(AppCompatResources.getDrawable(this, R.drawable.button_blue_background));
    }

    private void showOverLockscreen() {
        setShowWhenLocked(true);
        setTurnScreenOn(true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (km != null) {
            km.requestDismissKeyguard(this, null);
        }
    }

    private void initSoundPool() {
        if (soundPool != null) return;
        try {
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            soundPool = new SoundPool.Builder()
                    .setMaxStreams(1)
                    .setAudioAttributes(audioAttributes)
                    .build();
            soundPool.setOnLoadCompleteListener((pool, sampleId, status) -> {
                if (status == 0 && sampleId == popupSoundId) {
                    soundLoaded = true;
                    if (shouldPlaySoundWhenLoaded) {
                        playSoundInternal();
                    }
                }
            });
            popupSoundId = soundPool.load(this, R.raw.popup_sound, 1);
        } catch (RuntimeException e) {
            Log.e(TAG, "Error initializing sound pool", e);
        }
    }

    private void initViews() {
        barrierNameText = findViewById(R.id.textBarrierName);
        autoDismissText = findViewById(R.id.textAutoDismiss);

        findViewById(R.id.btnYes).setOnClickListener(v -> openBarrier());
        findViewById(R.id.btnNo).setOnClickListener(v -> dismissPopup());
        btnWait = findViewById(R.id.btnWait);
        btnWait.setOnClickListener(v -> cancelAutoDismiss());
        btnWait.setEnabled(true);
        btnWait.setBackground(AppCompatResources.getDrawable(this, R.drawable.button_blue_background));
    }

    private void getIntentData() {
        Intent intent = getIntent();
        barrierName = intent.getStringExtra("barrier_name");
        phoneNumber = intent.getStringExtra("barrier_phone");
        primaryId = intent.getLongExtra("barrier_id", -1);
        nearbyNames = intent.getStringArrayExtra("nearby_names");
        nearbyPhones = intent.getStringArrayExtra("nearby_phones");
    }

    /**
     * Subscribes to location for the lifetime of the popup so the "also in range" list updates
     * live — a barrier with a smaller radius that the car only enters after the popup appeared
     * will show up as soon as we cross into its zone.
     */
    private void startLocationTracking() {
        DatabaseHelper db = new DatabaseHelper(this);
        allBarriers = db.getAllBarriers();
        db.close();
        popupLocationManager = new LocationManager(this);
        popupLocationManager.startLocationUpdates(popupLocationCallback);
    }

    /** Recomputes the in-zone list from the current location and re-renders if it changed. */
    private void recomputeNearby(Location location) {
        if (allBarriers == null) {
            return;
        }
        List<Barrier> inZone = new ArrayList<>();
        for (Barrier b : allBarriers) {
            if (b.getId() == primaryId) {
                continue; // The suggested barrier is shown above, not in the list.
            }
            if (distanceTo(location, b) <= b.getDetectionRadius()) {
                inZone.add(b);
            }
        }
        inZone.sort((a, b) -> Double.compare(distanceTo(location, a), distanceTo(location, b)));

        String[] names = new String[inZone.size()];
        String[] phones = new String[inZone.size()];
        long[] ids = new long[inZone.size()];
        for (int i = 0; i < inZone.size(); i++) {
            names[i] = inZone.get(i).getName();
            phones[i] = inZone.get(i).getPhoneNumber();
            ids[i] = inZone.get(i).getId();
        }
        // Signature keyed on the SET of ids (order-independent) so GPS jitter that merely reorders
        // equidistant rows doesn't tear down and re-inflate the list (which would flicker and could
        // swallow an in-progress tap on a call button). Only a membership change rebuilds.
        renderNearby(names, phones, idSetSignature(ids));
    }

    private static float distanceTo(Location location, Barrier barrier) {
        float[] d = new float[1];
        Location.distanceBetween(location.getLatitude(), location.getLongitude(),
                barrier.getLatitude(), barrier.getLongitude(), d);
        return d[0];
    }

    private static String idSetSignature(long[] ids) {
        long[] sorted = ids.clone();
        java.util.Arrays.sort(sorted);
        StringBuilder sb = new StringBuilder("id");
        for (long id : sorted) sb.append(id).append(',');
        return sb.toString();
    }

    /** Renders the nearby list from the intent extras (initial paint before the first fix). */
    private void buildNearbyList() {
        renderNearby(nearbyNames, nearbyPhones, nearbySignature(nearbyNames, nearbyPhones));
    }

    /**
     * Renders the "also in range" list: every barrier whose zone the car is inside, regardless of
     * direction, each with its own call button. Skips the rebuild when the set is unchanged so the
     * per-second location updates don't flicker the rows or steal a tap mid-press.
     */
    private void renderNearby(String[] names, String[] phones, String signature) {
        if (signature.equals(lastNearbySignature)) {
            return;
        }
        lastNearbySignature = signature;

        View divider = findViewById(R.id.nearbyDivider);
        TextView header = findViewById(R.id.nearbyHeader);
        LinearLayout container = findViewById(R.id.nearbyContainer);
        container.removeAllViews();

        if (names == null || names.length == 0) {
            divider.setVisibility(View.GONE);
            header.setVisibility(View.GONE);
            container.setVisibility(View.GONE);
            return;
        }

        divider.setVisibility(View.VISIBLE);
        header.setVisibility(View.VISIBLE);
        container.setVisibility(View.VISIBLE);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < names.length; i++) {
            View row = inflater.inflate(R.layout.item_popup_nearby, container, false);
            TextView name = row.findViewById(R.id.nearbyName);
            Button call = row.findViewById(R.id.nearbyCall);

            name.setText(names[i]);
            final String phone = (phones != null && i < phones.length) ? phones[i] : null;
            call.setOnClickListener(v -> {
                placeCall(phone);
                dismissPopup();
            });
            container.addView(row);
        }
    }

    private static String nearbySignature(String[] names, String[] phones) {
        StringBuilder sb = new StringBuilder();
        if (names != null) {
            for (String n : names) sb.append(n).append('\u001F');
        }
        sb.append('\u001E');
        if (phones != null) {
            for (String p : phones) sb.append(p).append('\u001F');
        }
        return sb.toString();
    }

    private void updateUI() {
        if (barrierName != null) {
            barrierNameText.setText(getString(R.string.popup_title_window, barrierName));
        }
        autoDismissText.setVisibility(View.VISIBLE);
        autoDismissText.setText(getString(R.string.auto_dismiss,
                (int) (AUTO_DISMISS_DELAY_MS / 1000L)));
    }

    private void restartCountdown() {
        if (countdown != null) {
            countdown.cancel();
        }
        autoDismissText.setVisibility(View.VISIBLE);
        countdown = new CountDownTimer(AUTO_DISMISS_DELAY_MS, COUNTDOWN_TICK_MS) {
            @Override
            public void onTick(long millisUntilFinished) {
                int seconds = (int) Math.ceil(millisUntilFinished / 1000.0);
                autoDismissText.setText(getString(R.string.auto_dismiss, seconds));
            }

            @Override
            public void onFinish() {
                dismissPopup();
            }
        };
        countdown.start();
    }

    private void cancelAutoDismiss() {
        if (countdown != null) {
            countdown.cancel();
            countdown = null;
        }
        autoDismissText.setVisibility(View.GONE);
        btnWait.setEnabled(false);
        btnWait.setBackground(AppCompatResources.getDrawable(this, R.drawable.button_gray_background));
    }

    private void playSound() {
        if (soundLoaded) {
            playSoundInternal();
        } else {
            shouldPlaySoundWhenLoaded = true;
        }
    }

    private void playSoundInternal() {
        if (soundPool == null) return;
        try {
            soundPool.play(popupSoundId, 1.0f, 1.0f, 1, 0, 1.0f);
        } catch (RuntimeException e) {
            Log.e(TAG, "Error playing sound", e);
        }
    }

    private void openBarrier() {
        placeCall(phoneNumber);
        dismissPopup();
    }

    private void placeCall(String phone) {
        CallHelper.placeCall(this, phone);
    }

    private void dismissPopup() {
        if (countdown != null) {
            countdown.cancel();
            countdown = null;
        }
        finish();
    }

    @Override
    protected void onDestroy() {
        if (countdown != null) {
            countdown.cancel();
            countdown = null;
        }
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
        if (popupLocationManager != null) {
            popupLocationManager.destroy();
            popupLocationManager = null;
        }
        cancelPopupNotification();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        dismissPopup();
    }
}
