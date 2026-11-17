package com.example.socketmobile.android.hellocapture;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.socketmobile.android.hellocapture.databinding.DeviceListItemBinding;
import com.socketmobile.capture.client.DeviceClient;
import com.socketmobile.capture.client.DeviceState;
import com.socketmobile.capture.types.DeviceType;

import java.util.ArrayList;
import java.util.Map;

public class DeviceListAdapter extends RecyclerView.Adapter<DeviceListAdapter.ViewHolder>{

    ArrayList<DeviceClient> deviceList;

    Map<String, DeviceState> deviceStates;
    DeviceItemEventListener eventListener;

    public DeviceListAdapter(ArrayList<DeviceClient> deviceList, Map<String, DeviceState> deviceStates, DeviceItemEventListener eventListener) {
        this.deviceList = deviceList;
        this.deviceStates = deviceStates;
        this.eventListener = eventListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        DeviceListItemBinding binding = DeviceListItemBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding, eventListener);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DeviceClient device = deviceList.get(position);

        DeviceState state = deviceStates.get(device.getDeviceGuid());

        holder.setDeviceInfo(device, state);
    }

    @Override
    public int getItemCount() {

        if (deviceList == null)
            return 0;

        return deviceList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        private final DeviceListItemBinding binding;
        private DeviceClient device;

        private final Button[] btnList;

        private final DeviceItemEventListener eventListener;

        public ViewHolder(@NonNull DeviceListItemBinding binding, DeviceItemEventListener eventListener) {
            super(binding.getRoot());

            this.binding = binding;
            this.eventListener = eventListener;

            btnList = new Button[]{binding.btnTriggerSocketCam, binding.btnTriggerContinuous};
            for (Button btn : btnList) {
                btn.setOnClickListener(this);
            }
        }

        public void setDeviceInfo(DeviceClient device, DeviceState state) {
            this.device = device;
            binding.txtDeviceName.setText(device.getDeviceName());

            // A device restored from CaptureClient.getDeviceClientList() has no
            // DeviceStateEvent yet; a listed device is connected, so treat it as ready
            int stateValue = (state != null) ? state.intValue() : DeviceState.READY;
            handleColor(stateValue);

            for (Button btn : btnList) {
                btn.setEnabled(stateValue == DeviceState.READY);
            }

            // The software trigger buttons apply only to a SocketCam (camera)
            // device; a physical scanner is triggered with its hardware button,
            // so hide both for physical scanners.
            boolean isSocketCam =
                    DeviceType.retrieveInterfaceType(device.getDeviceType()) == DeviceType.InterfaceType.kNone;
            binding.btnTriggerSocketCam.setVisibility(isSocketCam ? View.VISIBLE : View.GONE);
            binding.btnTriggerContinuous.setVisibility(isSocketCam ? View.VISIBLE : View.GONE);
        }

        private void handleColor(int deviceState) {
            int color;
            int message;
            switch (deviceState) {
                default:
                case DeviceState.GONE:
                    // Gone
                    color = R.color.statusColorIdle;
                    message = R.string.status_gone;
                    break;
                case DeviceState.AVAILABLE:
                    // closed
                    color = R.color.statusColorError;
                    message = R.string.status_closed;
                    break;
                case DeviceState.OPEN:
                    // no focus
                    color = R.color.statusColorPending;
                    message = R.string.status_in_use;
                    break;
                case DeviceState.READY:
                    // focus
                    color = R.color.statusColorReady;
                    message = R.string.status_ready;
                    break;
            }
            binding.txtDeviceStatus.setTextColor(ContextCompat.getColor(binding.getRoot().getContext(), color));
            binding.txtDeviceStatus.setText(message);
        }

        @Override
        public void onClick(View v) {
            String deviceGuid = device.getDeviceGuid();
            int id_ = v.getId();
            if (id_ == R.id.btn_trigger_socket_cam){
                eventListener.onTriggerClicked(deviceGuid, false);
            } else if (id_ == R.id.btn_trigger_continuous){
                eventListener.onTriggerClicked(deviceGuid, true);
            }

        }
    }

    public interface DeviceItemEventListener {

        void onTriggerClicked(String deviceGuid, boolean isContinuousMode);
    }
}
