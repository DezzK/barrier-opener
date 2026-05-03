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
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showOverLockscreen();
        setContentView(R.layout.activity_barrier_popup);

        initViews();
        getIntentData();
        updateUI();
        startCountdown();
        initSoundPool();
        playSound();
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
    }

    private void updateUI() {
        if (barrierName != null) {
            barrierNameText.setText(getString(R.string.popup_title_window, barrierName));
        }
        autoDismissText.setVisibility(View.VISIBLE);
        autoDismissText.setText(getString(R.string.auto_dismiss,
                (int) (AUTO_DISMISS_DELAY_MS / 1000L)));
    }

    private void startCountdown() {
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
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            dismissPopup();
            return;
        }

        Uri telUri = Uri.parse("tel:" + phoneNumber.trim());
        Toast.makeText(this, getString(R.string.popup_calling_window, phoneNumber),
                Toast.LENGTH_SHORT).show();

        boolean canCallDirectly = ContextCompat.checkSelfPermission(this,
                Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED;
        Intent intent = new Intent(canCallDirectly ? Intent.ACTION_CALL : Intent.ACTION_DIAL,
                telUri);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (SecurityException e) {
            // CALL_PHONE was revoked between the check and the startActivity call — fall back to dialer.
            Intent dial = new Intent(Intent.ACTION_DIAL, telUri)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(dial);
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.error_call_permission_not_granted),
                    Toast.LENGTH_SHORT).show();
        }
        dismissPopup();
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
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        dismissPopup();
    }
}
