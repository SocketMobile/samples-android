package com.example.socketmobile.android.hellocaptureble;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.socketmobile.android.hellocaptureble.databinding.DeviceListItemBinding;
import com.socketmobile.capture.client.DeviceClient;
import com.socketmobile.capture.client.DeviceState;
import com.socketmobile.capture.types.DeviceType;

import java.util.ArrayList;
import java.util.Map;

public class DeviceListAdapter extends RecyclerView.Adapter<DeviceListAdapter.ViewHolder> {

    public interface DeviceItemEventListener {
        void onTriggerClicked(DeviceClient device, View anchor);
        void onThemeClicked(DeviceClient device, View anchor);
        void onDisconnectClicked(DeviceClient device);
        void onRemoveClicked(DeviceClient device);
    }

    private final ArrayList<DeviceClient> deviceList;
    // guid -> latest DeviceState, owned by MainActivity so the row can show each
    // device's own status instead of a single shared status line
    private final Map<String, Integer> deviceStates;
    private final DeviceItemEventListener eventListener;

    public DeviceListAdapter(ArrayList<DeviceClient> deviceList,
                             Map<String, Integer> deviceStates,
                             DeviceItemEventListener eventListener) {
        this.deviceList = deviceList;
        this.deviceStates = deviceStates;
        this.eventListener = eventListener;
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
        String name = device.getDeviceName();
        // The BARCODE/NFC tag only makes sense for physical scanners; a camera
        // scanner (SocketCam) is neither
        if (!DeviceType.isCameraScanner(device.getDeviceType())) {
            name += DeviceType.isBarcodeScanner(device.getDeviceType()) ? " BARCODE" : " NFC";
        }
        holder.binding.deviceName.setText(name);
        bindStatus(holder.binding.deviceStatus, deviceStates.get(device.getDeviceGuid()));

        holder.binding.btnTrigger.setOnClickListener(v -> eventListener.onTriggerClicked(device, v));
        holder.binding.btnTheme.setOnClickListener(v -> eventListener.onThemeClicked(device, v));
        holder.binding.btnDisconnect.setOnClickListener(v -> eventListener.onDisconnectClicked(device));
        holder.binding.btnRemove.setOnClickListener(v -> eventListener.onRemoveClicked(device));
    }

    /** Show the device's connection state in its own row, using the same colours
     *  as the service status. A listed device with no recorded state yet (e.g.
     *  restored after an activity recreation) is treated as READY. */
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

    @Override
    public int getItemCount() {
        return deviceList == null ? 0 : deviceList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final DeviceListItemBinding binding;

        public ViewHolder(@NonNull DeviceListItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
