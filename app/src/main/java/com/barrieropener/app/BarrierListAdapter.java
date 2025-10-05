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

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import java.util.List;
import java.util.Locale;

public class BarrierListAdapter extends BaseAdapter {
    private Context context;
    private List<Barrier> barriers;
    private LayoutInflater inflater;

    public BarrierListAdapter(Context context, List<Barrier> barriers) {
        this.context = context;
        this.barriers = barriers;
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

        return convertView;
    }

    private static class ViewHolder {
        TextView nameText;
        TextView phoneText;
        TextView locationText;
        TextView typeText;
    }
}
