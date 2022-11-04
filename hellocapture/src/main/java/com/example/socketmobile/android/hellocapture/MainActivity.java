package com.example.socketmobile.android.hellocapture;

import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.socketmobile.capture.CaptureError;
import com.socketmobile.capture.Property;
import com.socketmobile.capture.SocketCamStatus;
import com.socketmobile.capture.android.Capture;
import com.socketmobile.capture.android.events.ConnectionStateEvent;
import com.socketmobile.capture.client.CaptureClient;
import com.socketmobile.capture.client.ConnectionState;
import com.socketmobile.capture.client.DataEvent;
import com.socketmobile.capture.client.DeviceClient;
import com.socketmobile.capture.client.DeviceState;
import com.socketmobile.capture.client.DeviceStateEvent;
import com.socketmobile.capture.client.callbacks.PropertyCallback;
import com.socketmobile.capture.socketcam.client.CaptureExtension;
import com.socketmobile.capture.troy.ExtensionScope;
import com.socketmobile.capture.types.DecodedData;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity{

    private static final String TAG = MainActivity.class.getName();

    private static final String APP_CENTER_UUID = "f31d5d6d-957d-4598-ad0b-b9d79a15378a";

    private static final int COLOR_ERROR = Color.parseColor("#C72C30");
    private static final int COLOR_PENDING = Color.parseColor("#FF7400");
    private static final int COLOR_READY = Color.parseColor("#2AA324");
    private static final int COLOR_IDLE = Color.parseColor("#CCCCCC");

    private Snackbar socketSnack;

    private CaptureClient mCaptureClient;

    private CaptureExtension mCaptureExtension;

    // Map : DeviceGUID -> Device
    public static Map<String, DeviceClient> mDeviceMap = new HashMap<>();

    // Map : DeviceGUID -> DeviceState
    private Map<String, DeviceState> mDeviceStates = new HashMap<>();

    private ArrayList<DeviceClient> deviceList = new ArrayList<>();

    private DeviceListAdapter deviceListAdapter;

    private SwitchCompat switchSocketCam;

    private int mTriggerCount;

    private int mDecodedDataCount;

    private View btnSocketCamStatusArray[];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Start Capture
        Capture.builder(getApplicationContext())
                .enableLogging(BuildConfig.DEBUG)
                .build();

        setTitle(getResources().getString(R.string.app_name) + " " + BuildConfig.VERSION_NAME);

        mTriggerCount = 0;
        mDecodedDataCount = 0;
        TextView tv = (TextView) findViewById(R.id.scan_counter);
        tv.setText(String.format("%d / %d", mDecodedDataCount, mTriggerCount));


        switchSocketCam = findViewById(R.id.switch_socketcam);
        switchSocketCam.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    startSocketCamExtension();
                } else {
                    stopSocketCamExtension();
                }
            }
        });

        ArrayList scopeList = new ArrayList();
        scopeList.add(ExtensionScope.LOCAL);
        scopeList.add(ExtensionScope.GLOBAL);

        Spinner spinner = (Spinner) findViewById(R.id.spinner_scope);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, scopeList);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        findSocketCamStatusButtons();
        updateSocketCamStatusButton(false);

        // Connected device list
        RecyclerView recyclerView = findViewById(R.id.connected_device_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(MainActivity.this));

        deviceListAdapter = new DeviceListAdapter(deviceList, mDeviceStates, deviceItemEventListener);
        recyclerView.setAdapter(deviceListAdapter);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart() called with: " + "");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause() called with: " + "");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume() called with: " + "");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop() called with: " + "");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCaptureExtension != null)
            mCaptureExtension.stop();
    }

    // Enable SocketCam Status
    public void updateSocketCamStatus(View view) {
        if(mCaptureClient == null || mCaptureExtension == null) {
            Toast.makeText(getApplicationContext(), "SocketCam client is not initialized", Toast.LENGTH_SHORT).show();
            return;
        }

        byte status = SocketCamStatus.ENABLE;
        switch(view.getId()){
            case R.id.btn_socketcam_status_enable:
                status = SocketCamStatus.ENABLE;
                break;
            case R.id.btn_socketcam_status_disable:
                status = SocketCamStatus.DISABLE;
                break;
            case R.id.btn_socketcam_status_supported:
                status = SocketCamStatus.SUPPORTED;
                break;
            case R.id.btn_socketcam_status_not_supported:
                status = SocketCamStatus.NOT_SUPPORTED;
                break;
        }
        mCaptureClient.setSocketCamStatus(status, mSetPropertyCallback);
    }

    public void startSocketCamExtension() {
        if(mCaptureClient != null) {
            Log.d(TAG, "SocketCam: start extension");

            Spinner spinner = (Spinner) findViewById(R.id.spinner_scope);
            ExtensionScope scope = (ExtensionScope)spinner.getSelectedItem();

            // Start Capture Extension
            mCaptureExtension = new CaptureExtension.Builder()
                    .setContext(this)
                    .setClientHandle(mCaptureClient.getHandle())
                    .setExtensionScope(scope)
                    .setListener(mListener)
                    .build();
            mCaptureExtension.start();

            updateSocketCamStatusButton(true);
        } else {
            Toast.makeText(getApplicationContext(), "Capture client is not initialized", Toast.LENGTH_SHORT).show();
        }
    }

    public void stopSocketCamExtension() {
        if(mCaptureExtension != null) {
            mCaptureExtension.stop();
            updateSocketCamStatusButton(false);
        } else {
            Toast.makeText(getApplicationContext(), "Capture extension is not initialized", Toast.LENGTH_SHORT).show();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onScan(DataEvent event) {
        Log.d(TAG, "onScan : event = [" + event + "]");
        DecodedData data = event.getData();
        if (data.result == DecodedData.RESULT_SUCCESS) {
            incrementScanCount(event.getData().getString());
        } else {
            Log.d(TAG, "Scan Cancelled");
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    public void onCaptureServiceConnectionStateChange(ConnectionStateEvent event) {
        Log.d(TAG, "onService() called with: event = [" + event.getState().intValue() + "]");
        if (socketSnack != null) {
            socketSnack.dismiss();
        }

        TextView tv = findViewById(R.id.main_service_status);

        ConnectionState state = event.getState();
        mCaptureClient = event.getClient();
        if (state.hasError()) {
            setText(tv, COLOR_ERROR, "Error");

            CaptureError error = state.getError();
            if (error.getCode() == CaptureError.COMPANION_NOT_INSTALLED) {
                // Prompt to install
                socketSnack = Snackbar.make(findViewById(R.id.main_content),
                        "Socket Mobile Companion must be installed to use your scanner",
                        Snackbar.LENGTH_INDEFINITE);
                socketSnack.setAction("Install", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // Install
                        Capture.installCompanion(v.getContext());
                        socketSnack.dismiss();
                    }
                });
                socketSnack.show();
            } else if (error.getCode() == CaptureError.SERVICE_NOT_RUNNING) {
                // The first time you receive this error, the client may still be disconnecting.
                // Wait until the client is disconnected before attempting to restart
                if (state.isDisconnected()) {
                    if (Capture.notRestartedRecently()) {
                        Capture.restart(this);
                    } else {
                        // Something is seriously wrong
                        socketSnack = Snackbar.make(findViewById(R.id.main_content),
                                "Please restart Companion and this app",
                                Snackbar.LENGTH_INDEFINITE);
                        socketSnack.show();
                    }
                }
            } else if (error.getCode() == CaptureError.BLUETOOTH_NOT_ENABLED) {
                socketSnack = Snackbar.make(findViewById(R.id.main_content),
                        "Bluetooth must be enabled to use your scanner",
                        Snackbar.LENGTH_INDEFINITE);
                socketSnack.setAction("Enable", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // Enable Bluetooth - requires BLUETOOTH permission
                        startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
                    }
                });
                socketSnack.show();
            } else {
                socketSnack = Snackbar.make(findViewById(R.id.main_content), error.getMessage(),
                        Snackbar.LENGTH_INDEFINITE);
                socketSnack.show();
            }
        } else {
            // Connection has been established. You will be notified when a device is connected and ready.
            // do something or do nothing
            switch (state.intValue()) {
                case ConnectionState.CONNECTING:
                    setText(tv, COLOR_PENDING, "Connecting");
                    break;
                case ConnectionState.CONNECTED:
                    setText(tv, COLOR_PENDING, "Connected");
                    break;
                case ConnectionState.READY:
                    setText(tv, COLOR_READY, "Ready");
                    break;
                case ConnectionState.DISCONNECTING:
                    setText(tv, COLOR_PENDING, "Disconnecting");
                    break;
                case ConnectionState.DISCONNECTED:
                    setText(tv, COLOR_IDLE, "Disconnected");
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    public void onCaptureDeviceStateChange(DeviceStateEvent event) {
        DeviceClient device = event.getDevice();
        DeviceState state = event.getState();

        Log.d(TAG, "Device : type : " + device.getDeviceType() + " guid : " + device.getDeviceGuid());
        Log.d(TAG, "Device : name : " + device.getDeviceName() + " state: " + state.intValue());
        String deviceGUID = device.getDeviceGuid();
        if (!mDeviceMap.containsKey(deviceGUID)) {
            deviceList.add(device);
        }

        if (state.intValue() == DeviceState.GONE) {
            mDeviceMap.remove(deviceGUID);
            mDeviceStates.remove(deviceGUID);
            deviceList.remove(device);
        }

        mDeviceMap.put(deviceGUID, device);
        mDeviceStates.put(deviceGUID, state);
        deviceListAdapter.notifyDataSetChanged();
    }

    private void incrementScanCount(String message) {
        ((TextView) findViewById(R.id.hello_scan)).append("\n" + message);
        mDecodedDataCount++;
        TextView tv = (TextView) findViewById(R.id.scan_counter);
        tv.setText(String.format("%d / %d", mDecodedDataCount, mTriggerCount));
        Log.d(TAG, String.format("Decoded Data => Decoded Data Count / Trigger Count: %d / %d", mDecodedDataCount,mTriggerCount));
    }

    private void setText(TextView textView, int color, String text) {
        textView.setText(text);
        textView.setTextColor(color);
    }

    // Response for get Property calls are received here
    PropertyCallback mSetPropertyCallback = new PropertyCallback() {

        @Override
        public void onComplete(final CaptureError error, final Property property) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (error != null) {
                        String err = String.format(getResources().getString(R.string.set_property_error), "" + error.getCode());
                        Toast.makeText(getApplicationContext(), err, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getApplicationContext(), R.string.set_property_complete, Toast.LENGTH_SHORT).show();
                        switch (property.id) {
                            case Property.SOCKETCAM_STATUS:
                                mCaptureClient.getSocketCamStatus(mGetPropertyCallback);
                                break;
                            default:
                                //Nothing to do here
                                break;
                        }
                    }
                }
            });
        }
    };

    // Response for set Property calls are received here
    PropertyCallback mGetPropertyCallback = new PropertyCallback() {

        @Override
        public void onComplete(final CaptureError error, final Property property) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (error != null) {
                        String err = String.format(getResources().getString(R.string.get_property_error), "" + error.getCode());
                        Toast.makeText(getApplicationContext(), err, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getApplicationContext(), R.string.get_property_complete, Toast.LENGTH_SHORT).show();
                        switch (property.id) {
                            case Property.SOCKETCAM_STATUS:
                                TextView currentSocketCamStatus = (TextView) findViewById(R.id.socketcam_current_status_text);
                                if(property.byte_ != null) {
                                    currentSocketCamStatus.setText(property.byte_ + " " + SocketCamStatus.getString(property.byte_));
                                } else {
                                    currentSocketCamStatus.setText("Unknown*");
                                }
                                break;
                            default:
                                //Nothing to do here
                                break;
                        }
                    }
                }
            });
        }
    };

    // Events related to capture extension for using SocketCam will be sent here
    CaptureExtension.Listener mListener = new CaptureExtension.Listener() {
        @Override
        public void onExtensionStateChanged(ConnectionState connectionState) {
            Log.d(TAG, "onExtensionStarted");
            switch (connectionState.intValue()) {
                case ConnectionState.READY:
                    // Capture Extension has started successfully
                    // Enable SocketCam Status, if it has not been Enabled already
                    // Do your UI update here
                    break;
                case ConnectionState.DISCONNECTED:
                    // Capture Extension has been stopped
                    // Do your UI update here
                    break;
                default:
                    break;
            }
        }

        @Override
        public void onError(CaptureError error) {
            Log.e(TAG, "SocketCam: onError " + error);
        }
    };

    //region SocketCam Status buttons
    private void findSocketCamStatusButtons() {
        int viewIds[] = {R.id.btn_socketcam_status_enable, R.id.btn_socketcam_status_disable,
                R.id.btn_socketcam_status_not_supported, R.id.btn_socketcam_status_supported,
                R.id.btn_socketcam_status_read};
        btnSocketCamStatusArray = new View[viewIds.length];
        for (int i = 0; i < viewIds.length; i++) {
            btnSocketCamStatusArray[i] = findViewById(viewIds[i]);
        }

        findViewById(R.id.btn_socketcam_status_read).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCaptureClient.getSocketCamStatus(mGetPropertyCallback);
            }
        });
    }

    private void updateSocketCamStatusButton(boolean enabled) {
        Log.e(TAG, "updateSocketCamStatusButtons : " + enabled);
        if (!enabled) {
            TextView currentSocketCamStatus = (TextView) findViewById(R.id.socketcam_current_status_text);
            currentSocketCamStatus.setText("");
        }

        for (View btn : btnSocketCamStatusArray) {
            btn.setEnabled(enabled);
        }
    }
    //endregion

    //region clear data
    public void clearScanData(View view) {
        mDecodedDataCount = 0;
        mTriggerCount = 0;
        ((TextView) findViewById(R.id.hello_scan)).setText(R.string.socket_cam_scan_data);
        TextView tv = (TextView) findViewById(R.id.scan_counter);
        tv.setText(String.format("%d / %d", mDecodedDataCount, mTriggerCount));
    }
    //endregion

    //region DeviceListItem event
    DeviceListAdapter.DeviceItemEventListener deviceItemEventListener = new DeviceListAdapter.DeviceItemEventListener() {
        @Override
        public void onTriggerClicked(String deviceGUID, boolean isContinuousMode) {
            DeviceClient currentDevice = mDeviceMap.get(deviceGUID);

            if(currentDevice == null) {
                Toast.makeText(getApplicationContext(), "SocketCam not found. Did you enable SocketCam?", Toast.LENGTH_LONG).show();
                return;
            }

            if (isContinuousMode) {
                currentDevice.triggerContinuous(mSetPropertyCallback);
            } else {
                currentDevice.trigger(mSetPropertyCallback);
            }

            mTriggerCount++;
            TextView tv = (TextView) findViewById(R.id.scan_counter);
            tv.setText(String.format("%d / %d", mDecodedDataCount, mTriggerCount));
            Log.d(TAG, String.format("Trigger => Decoded Data Count / Trigger Count: %d / %d", mDecodedDataCount,mTriggerCount));
        }
    };
    //endregion
}

