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
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 123;
    private static final int OVERLAY_PERMISSION_REQUEST_CODE = 124;

    private ListView barrierListView;
    private FloatingActionButton fabAdd;
    private DatabaseHelper dbHelper;
    private BarrierListAdapter adapter;
    private List<Barrier> barriers;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        dbHelper = new DatabaseHelper(this);

        checkPermissions();
        loadBarriers();
        startLocationService();
    }

    private void initViews() {
        barrierListView = findViewById(R.id.barrierListView);
        fabAdd = findViewById(R.id.fabAdd);

        fabAdd.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, AddEditBarrierActivity.class);
            startActivity(intent);
        });

        barrierListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Barrier barrier = barriers.get(position);
                Intent intent = new Intent(MainActivity.this, AddEditBarrierActivity.class);
                intent.putExtra("barrier_id", barrier.getId());
                startActivity(intent);
            }
        });

        barrierListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                showDeleteDialog(barriers.get(position));
                return true;
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadBarriers();
    }

    private void checkPermissions() {
        String[] permissions = {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.CALL_PHONE
        };

        boolean hasAllPermissions = true;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                hasAllPermissions = false;
                break;
            }
        }

        if (!hasAllPermissions) {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
        }

        // Check overlay permission for Android 6+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                showOverlayPermissionDialog();
            }
        }
    }

    private void showOverlayPermissionDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.overlay_permission_title)
                .setMessage(R.string.overlay_permission_message)
                .setPositiveButton(R.string.grant_permission, (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName()));
                    startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (!allGranted) {
                Toast.makeText(this, R.string.permissions_required, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void startLocationService() {
        startForegroundService(new Intent(this, LocationService.class));
    }

    private void loadBarriers() {
        barriers = dbHelper.getAllBarriers();

        if (adapter == null) {
            adapter = new BarrierListAdapter(this, barriers);
            barrierListView.setAdapter(adapter);
        } else {
            adapter.updateBarriers(barriers);
        }
    }

    private void showDeleteDialog(Barrier barrier) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_barrier_title)
                .setMessage(getString(R.string.delete_barrier_message, barrier.getName()))
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    dbHelper.deleteBarrier(barrier.getId());
                    loadBarriers();
                    Toast.makeText(this, R.string.barrier_deleted, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dbHelper != null) {
            dbHelper.close();
        }
    }
}
