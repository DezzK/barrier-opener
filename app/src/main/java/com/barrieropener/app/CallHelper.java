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
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.telecom.TelecomManager;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

/**
 * Places a call to a barrier's phone number, trying every path a head unit might support.
 *
 * Head-unit firmwares vary wildly: some ship a stock dialer activity (ACTION_CALL works), some
 * only route calls through the telecom framework + Bluetooth HFP (TelecomManager.placeCall works,
 * no dialer activity exists), some only expose a dial pad (ACTION_DIAL). The cascade tries them
 * in that order and only reports failure when nothing on the device can place a call — with an
 * honest message, not a misleading "no permission" toast.
 */
public final class CallHelper {
    private static final String TAG = "CallHelper";

    private CallHelper() {}

    public static void placeCall(Context context, String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return;
        }
        String number = phone.trim();
        // fromParts (not Uri.parse) so '#' and '*' in DTMF-style barrier numbers are properly
        // encoded instead of being cut off as a URI fragment.
        Uri telUri = Uri.fromParts("tel", number, null);

        Toast.makeText(context, context.getString(R.string.popup_calling_window, number),
                Toast.LENGTH_SHORT).show();

        boolean hasCallPermission = ContextCompat.checkSelfPermission(context,
                Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED;

        // 1) Direct call through a dialer activity.
        if (hasCallPermission) {
            try {
                context.startActivity(new Intent(Intent.ACTION_CALL, telUri)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (ActivityNotFoundException | SecurityException e) {
                Log.w(TAG, "ACTION_CALL failed, falling back", e);
            }
        }

        // 2) Telecom framework — the canonical automotive path: routes through the BT-HFP
        //    connected phone even when the firmware ships no dialer activity at all.
        if (hasCallPermission) {
            TelecomManager tm = context.getSystemService(TelecomManager.class);
            if (tm != null) {
                try {
                    tm.placeCall(telUri, null);
                    return;
                } catch (Exception e) {
                    Log.w(TAG, "TelecomManager.placeCall failed, falling back", e);
                }
            }
        }

        // 3) Dial pad with the number pre-filled (no permission needed).
        try {
            context.startActivity(new Intent(Intent.ACTION_DIAL, telUri)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return;
        } catch (Exception e) {
            Log.e(TAG, "No way to place a call on this device", e);
        }

        Toast.makeText(context,
                hasCallPermission
                        ? context.getString(R.string.error_no_call_app)
                        : context.getString(R.string.error_call_permission_not_granted),
                Toast.LENGTH_LONG).show();
    }
}
