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
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import java.util.List;
import java.util.Locale;

public class BarrierListAdapter extends BaseAdapter {

    public interface Callbacks {
        void onBarriersChanged();
    }

    private final Context context;
    private final Callbacks callbacks;
    private List<Barrier> barriers;
    private final DatabaseHelper dbHelper;
    private final LayoutInflater inflater;

    public BarrierListAdapter(Context context, Callbacks callbacks,
                              List<Barrier> barriers, DatabaseHelper dbHelper) {
        this.context = context;
        this.callbacks = callbacks;
        this.barriers = barriers;
        this.dbHelper = dbHelper;
        this.inflater = LayoutInflater.from(context);
    }

    public void updateBarriers(List<Barrier> newBarriers) {
        this.barriers = newBarriers;
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return barriers.size();
    }

    @Override
    public Object getItem(int position) {
        return barriers.get(position);
    }

    @Override
    public long getItemId(int position) {
        return barriers.get(position).getId();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_barrier, parent, false);
            holder = new ViewHolder();
            holder.callButton = convertView.findViewById(R.id.callButton);
            holder.nameText = convertView.findViewById(R.id.textBarrierName);
            holder.phoneText = convertView.findViewById(R.id.textBarrierPhone);
            holder.locationText = convertView.findViewById(R.id.textBarrierLocation);
            holder.typeText = convertView.findViewById(R.id.textBarrierType);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        Barrier barrier = barriers.get(position);
        holder.nameText.setText(barrier.getName());
        holder.phoneText.setText(barrier.getPhoneNumber());
        holder.locationText.setText(String.format(Locale.getDefault(),
                context.getString(R.string.location_format),
                barrier.getLatitude(),
                barrier.getLongitude(),
                barrier.getDetectionRadius()));
        if (barrier.getBarrierType() == Barrier.BarrierType.ONE_WAY) {
            holder.typeText.setText(R.string.barrier_type_one_way);
        } else {
            holder.typeText.setText(R.string.barrier_type_bidirectional);
        }

        holder.callButton.setOnClickListener(v -> {
            String phoneNumber = barrier.getPhoneNumber();
            Uri telUri = Uri.parse("tel:" + phoneNumber.trim());
            Toast.makeText(context, context.getString(R.string.popup_calling_window, phoneNumber),
                    Toast.LENGTH_SHORT).show();

            boolean canCallDirectly = ContextCompat.checkSelfPermission(context,
                    Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED;
            Intent intent = new Intent(canCallDirectly ? Intent.ACTION_CALL : Intent.ACTION_DIAL,
                    telUri);
            try {
                context.startActivity(intent);
            } catch (SecurityException e) {
                context.startActivity(new Intent(Intent.ACTION_DIAL, telUri));
            }
        });

        convertView.setOnClickListener((view) -> {
            Intent intent = new Intent(context, AddEditBarrierActivity.class);
            intent.putExtra("barrier_id", barrier.getId());
            context.startActivity(intent);
        });

        convertView.setOnLongClickListener((view) -> {
            new AlertDialog.Builder(context)
                    .setTitle(R.string.delete_barrier_title)
                    .setMessage(context.getString(R.string.delete_barrier_message, barrier.getName()))
                    .setPositiveButton(R.string.delete, (dialog, which) -> {
                        dbHelper.deleteBarrier(barrier.getId());
                        BarrierRepository.notifyChanged(context);
                        callbacks.onBarriersChanged();
                        Toast.makeText(context, R.string.barrier_deleted, Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();

            return true;
        });

        return convertView;
    }

    private static class ViewHolder {
        Button callButton;
        TextView nameText;
        TextView phoneText;
        TextView locationText;
        TextView typeText;
    }
}
