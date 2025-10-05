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
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.content.res.AppCompatResources;

public class BarrierPopupActivity extends Activity {
    private static final String TAG = "BarrierPopupActivity";
    private static final int AUTO_DISMISS_DELAY = 10000; // 10 seconds

    private MediaPlayer mediaPlayer;

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

        // Initialize media player
        mediaPlayer = MediaPlayer.create(this, R.raw.popup_sound);
        mediaPlayer.setLooping(false);
        mediaPlayer.setOnErrorListener((mp, what, extra) -> {
            Log.e(TAG, "MediaPlayer error. what: " + what + ", extra: " + extra);
            return false;
        });

        // Configure window to appear over other apps
        getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        setContentView(R.layout.activity_barrier_popup);

        initViews();
        getIntentData();
        updateUI();
        setupAutoDismiss();
        playSound();
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
        try {
            mediaPlayer.start();
        } catch (Exception e) {
            Log.e(TAG, "Error playing sound", e);
        }
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
                Log.e(TAG, "Call permission not granted", e);
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
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        dismissPopup();
    }
}
