package com.example.socketmobile.android.hellocapture;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

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
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.device_list_item, parent, false);

        return new ViewHolder(view, eventListener);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DeviceClient device = deviceList.get(position);

        String deviceName = device.getDeviceName();
        DeviceState state = deviceStates.get(deviceName);

        holder.setDeviceInfo(device, state);
    }

    @Override
    public int getItemCount() {

        if (deviceList == null)
            return 0;

        return deviceList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        private DeviceClient device;
        private DeviceState state;

        private TextView txtDeviceName;

        private TextView txtDeviceStatus;


        private static int[] btn_id_list = {R.id.btn_property, R.id.btn_trigger_socket_cam, R.id.btn_trigger_continuous};
        private Button[] btnList = new Button[btn_id_list.length];

        private DeviceItemEventListener eventListener;

        public ViewHolder(@NonNull View itemView, DeviceItemEventListener eventListener) {
            super(itemView);

            this.eventListener = eventListener;


            txtDeviceName = itemView.findViewById(R.id.txt_device_name);
            txtDeviceStatus = itemView.findViewById(R.id.txt_device_status);


            for (int i = 0; i < btn_id_list.length; i++) {
                btnList[i] = itemView.findViewById(btn_id_list[i]);
                btnList[i].setOnClickListener(this);
            }
        }

        public void setDeviceInfo(DeviceClient device, DeviceState state) {
            this.device = device;
            this.state = state;
            txtDeviceName.setText(device.getDeviceName());

            handleColor(txtDeviceStatus, state.intValue());

            for (Button btn : btnList) {
                btn.setEnabled(state.intValue() == DeviceState.READY);
            }

            if (DeviceType.retrieveInterfaceType(device.getDeviceType()) == DeviceType.InterfaceType.kNone) {
                // SocketCamDevice
                btnList[1].setVisibility(View.VISIBLE);
            } else {
                btnList[1].setVisibility(View.GONE);
            }
        }

        private void handleColor(TextView tv, int deviceState) {
            int color;
            String message;
            switch (deviceState) {
                default:
                case DeviceState.GONE:
                    // Gone
                    color = R.color.statusColorIdle;
                    message = "Gone";
                    break;
                case DeviceState.AVAILABLE:
                    // closed
                    color = R.color.statusColorError;
                    message = "Closed";
                    break;
                case DeviceState.OPEN:
                    // no focus
                    color = R.color.statusColorPending;
                    message = "In Use";
                    break;
                case DeviceState.READY:
                    // focus
                    color = R.color.statusColorReady;
                    message = "Ready";
                    break;
            }
            tv.setTextColor(tv.getContext().getResources().getColor(color));
            tv.setText(message);

            for (Button btn : btnList) {
                btn.setEnabled(state.intValue() == DeviceState.READY);
            }

        }

        @Override
        public void onClick(View v) {
            String deviceName = device.getDeviceName();
            int id_ = v.getId();
            if(id_ == R.id.btn_property) {
                eventListener.onSettingClicked(deviceName);
            } else if (id_ == R.id.btn_trigger_socket_cam){
                eventListener.onTriggerClicked(deviceName, false);
            } else if (id_ == R.id.btn_trigger_continuous){
                eventListener.onTriggerClicked(deviceName, true);
            }

        }
    }

    public interface DeviceItemEventListener {

        void onSettingClicked(String deviceName);
        void onTriggerClicked(String deviceName, boolean isContinuousMode);
    }
}
