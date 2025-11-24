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

import android.app.Activity;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.content.res.AppCompatResources;

public class BarrierPopupActivity extends Activity {
    private static final String TAG = "BarrierPopupActivity";
    private static final int AUTO_DISMISS_DELAY = 10000; // 10 seconds

    private SoundPool soundPool;
    private int popupSoundId;

    private TextView barrierNameText;
    private TextView autoDismissText;
    private Button btnWait;
    private Handler dismissHandler;
    private Runnable dismissRunnable;

    private String barrierName;
    private String phoneNumber;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Configure window to appear over other apps
        getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        getWindow().addFlags(PixelFormat.TRANSLUCENT);

        setContentView(R.layout.activity_barrier_popup);

        initViews();
        getIntentData();
        updateUI();
        setupAutoDismiss();
        initSoundPool();
        playSound();
    }

    private void initSoundPool() {
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        soundPool = new SoundPool.Builder()
                .setMaxStreams(1)
                .setAudioAttributes(audioAttributes)
                .build();
        popupSoundId = soundPool.load(this, R.raw.popup_sound, 1);
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
    }

    private void updateUI() {
        if (barrierName != null) {
            String title = getString(R.string.popup_title_window, barrierName);
            barrierNameText.setText(title);
        }

        autoDismissText.setVisibility(View.VISIBLE);
        // TODO: Update text in timer
        autoDismissText.setText(String.format(getString(R.string.auto_dismiss), AUTO_DISMISS_DELAY / 1000));
    }

    private void setupAutoDismiss() {
        dismissHandler = new Handler();
        dismissRunnable = this::dismissPopup;
        dismissHandler.postDelayed(dismissRunnable, AUTO_DISMISS_DELAY);
    }

    private void cancelAutoDismiss() {
        if (dismissHandler != null && dismissRunnable != null) {
            dismissHandler.removeCallbacks(dismissRunnable);
            autoDismissText.setVisibility(View.GONE);
        }
        btnWait.setEnabled(false);
        btnWait.setBackground(AppCompatResources.getDrawable(this, R.drawable.button_gray_background));
    }

    private void playSound() {
        soundPool.play(popupSoundId, 1.0f, 1.0f, 1, 0, 1.0f);
    }

    private void openBarrier() {
        if (phoneNumber != null && !phoneNumber.isEmpty()) {
            String phoneNumberUri = "tel:" + phoneNumber.trim();
            Intent callIntent = new Intent(Intent.ACTION_CALL, Uri.parse(phoneNumberUri));

            // Show a toast with the calling message
            String callingMessage = getString(R.string.popup_calling_window, phoneNumber);
            Toast.makeText(this, callingMessage, Toast.LENGTH_SHORT).show();

            try {
                startActivity(callIntent);
            } catch (SecurityException e) {
                Toast.makeText(this, R.string.error_call_permission_not_granted, Toast.LENGTH_SHORT).show();
            }
        }
        dismissPopup();
    }

    private void dismissPopup() {
        if (dismissHandler != null && dismissRunnable != null) {
            dismissHandler.removeCallbacks(dismissRunnable);
        }
        finish();
    }

    @Override
    protected void onDestroy() {
        if (dismissHandler != null && dismissRunnable != null) {
            dismissHandler.removeCallbacks(dismissRunnable);
        }
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        dismissPopup();
    }
}
