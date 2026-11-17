package com.example.socketmobile.android.hellocaptureplainjava;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.socketmobile.android.hellocaptureplainjava.databinding.DeviceListItemBinding;
import com.socketmobile.capture.client.DeviceClient;
import com.socketmobile.capture.client.DeviceState;

import java.util.ArrayList;
import java.util.Map;

/**
 * Shows each connected device with its own status, matching the other samples in
 * this project. The list and the guid -> state map are owned by MainActivity.
 */
public class DeviceListAdapter extends RecyclerView.Adapter<DeviceListAdapter.ViewHolder> {

    private final ArrayList<DeviceClient> deviceList;
    private final Map<String, Integer> deviceStates;

    public DeviceListAdapter(ArrayList<DeviceClient> deviceList, Map<String, Integer> deviceStates) {
        this.deviceList = deviceList;
        this.deviceStates = deviceStates;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        DeviceListItemBinding binding = DeviceListItemBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DeviceClient device = deviceList.get(position);
        holder.binding.deviceName.setText(device.getDeviceName());
        bindStatus(holder.binding.deviceStatus, deviceStates.get(device.getDeviceGuid()));
    }

    @Override
    public int getItemCount() {
        return deviceList == null ? 0 : deviceList.size();
    }

    /** Render the device's connection state in its row. A listed device with no
     *  recorded state yet is treated as READY. */
    private void bindStatus(TextView tv, Integer stateValue) {
        int state = stateValue != null ? stateValue : DeviceState.READY;
        int colorRes;
        int messageRes;
        switch (state) {
            case DeviceState.AVAILABLE:
                colorRes = R.color.statusColorError;
                messageRes = R.string.status_closed;
                break;
            case DeviceState.OPEN:
                colorRes = R.color.statusColorPending;
                messageRes = R.string.status_in_use;
                break;
            case DeviceState.READY:
                colorRes = R.color.statusColorReady;
                messageRes = R.string.status_ready;
                break;
            default:
                colorRes = R.color.statusColorIdle;
                messageRes = R.string.status_gone;
                break;
        }
        tv.setTextColor(ContextCompat.getColor(tv.getContext(), colorRes));
        tv.setText(messageRes);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final DeviceListItemBinding binding;

        public ViewHolder(@NonNull DeviceListItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
