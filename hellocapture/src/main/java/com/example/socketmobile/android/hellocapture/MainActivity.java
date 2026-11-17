package com.example.socketmobile.android.hellocapture;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.socketmobile.android.hellocapture.databinding.ActivityMainBinding;
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

    private static final int COLOR_ERROR = Color.parseColor("#C72C30");
    private static final int COLOR_PENDING = Color.parseColor("#FF7400");
    private static final int COLOR_READY = Color.parseColor("#2AA324");
    private static final int COLOR_IDLE = Color.parseColor("#CCCCCC");

    private ActivityMainBinding binding;
    private Snackbar socketSnack;
    private Snackbar btPermissionSnack;
    private CaptureClient mCaptureClient;
    private CaptureExtension mCaptureExtension;

    // Set when the on-screen trigger button is tapped; 0 when the scan was
    // started from the device itself (no "time to scan" is shown then)
    private long startTime = 0;

    // Map : DeviceGuid -> Device
    public Map<String, DeviceClient> mDeviceMap = new HashMap<>();

    // Map : DeviceGuid -> DeviceState
    private Map<String, DeviceState> mDeviceStates = new HashMap<>();

    private ArrayList<DeviceClient> deviceList = new ArrayList<>();
    private DeviceListAdapter deviceListAdapter;

    private int mTriggerCount;
    private int mDecodedDataCount;
    private int mDataCount = 0;

    private static final String STATE_DATA_COUNT = "dataCount";
    private static final String STATE_DECODED_COUNT = "decodedCount";
    private static final String STATE_TRIGGER_COUNT = "triggerCount";
    private static final String STATE_SCAN_DATA = "scanData";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        View rootView = binding.getRoot();
        final int padLeft = rootView.getPaddingLeft(), padTop = rootView.getPaddingTop(),
                padRight = rootView.getPaddingRight(), padBottom = rootView.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(padLeft + systemBars.left, padTop + systemBars.top,
                    padRight + systemBars.right, padBottom + systemBars.bottom);
            return insets;
        });

        setTitle(getString(R.string.app_name) + " " + BuildConfig.VERSION_NAME);

        mTriggerCount = 0;
        mDecodedDataCount = 0;
        if (savedInstanceState != null) {
            mDataCount = savedInstanceState.getInt(STATE_DATA_COUNT, 0);
            mDecodedDataCount = savedInstanceState.getInt(STATE_DECODED_COUNT, 0);
            mTriggerCount = savedInstanceState.getInt(STATE_TRIGGER_COUNT, 0);
            CharSequence scanData = savedInstanceState.getCharSequence(STATE_SCAN_DATA);
            if (scanData != null) {
                binding.helloScan.setText(scanData);
            }
        }
        updateScanCounter();

        binding.switchSocketcam.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked)
                startSocketCamExtension();
            else
                stopSocketCamExtension();
        });

        ArrayList<ExtensionScope> scopeList = new ArrayList<>();
        scopeList.add(ExtensionScope.LOCAL);
        scopeList.add(ExtensionScope.GLOBAL);

        ArrayAdapter<ExtensionScope> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, scopeList);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerScope.setAdapter(adapter);

        wireSocketCamStatusButtons();
        updateSocketCamStatusButton(false);

        // Connected device list
        binding.connectedDeviceList.setLayoutManager(new LinearLayoutManager(this));
        deviceListAdapter = new DeviceListAdapter(deviceList, mDeviceStates, deviceItemEventListener);
        binding.connectedDeviceList.setAdapter(deviceListAdapter);

        binding.btnClearData.setOnClickListener(this::clearScanData);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_DATA_COUNT, mDataCount);
        outState.putInt(STATE_DECODED_COUNT, mDecodedDataCount);
        outState.putInt(STATE_TRIGGER_COUNT, mTriggerCount);
        outState.putCharSequence(STATE_SCAN_DATA, binding.helloScan.getText());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCaptureExtension != null)
            mCaptureExtension.stop();
    }

    public void updateSocketCamStatus(View view) {
        if(mCaptureClient == null || mCaptureExtension == null) {
            Toast.makeText(getApplicationContext(), R.string.socketcam_not_initialized, Toast.LENGTH_SHORT).show();
            return;
        }

        byte status = SocketCamStatus.ENABLE;
        int id_ = view.getId();
        if (id_ == R.id.btn_socketcam_status_enable) {
            status = SocketCamStatus.ENABLE;
        } else if(id_ == R.id.btn_socketcam_status_disable) {
            status = SocketCamStatus.DISABLE;
        } else if(id_ == R.id.btn_socketcam_status_supported) {
            status = SocketCamStatus.SUPPORTED;
        }else if(id_ == R.id.btn_socketcam_status_not_supported) {
            status = SocketCamStatus.NOT_SUPPORTED;
        }

        mCaptureClient.setSocketCamStatus(status, mSetPropertyCallback);
    }

    private void startSocketCamExtension() {
        if(mCaptureClient != null) {
            mCaptureClient.getCaptureVersion((captureError, property) -> {
                if(property != null) {
                    Log.d(TAG, "SocketCam: Companion Service version : " + property.version);
                } else {
                    Log.d(TAG, "SocketCam: Companion Service version error : " + captureError.getCode());
                }
            });

            Log.d(TAG, "SocketCam: start extension");

            ExtensionScope scope = (ExtensionScope) binding.spinnerScope.getSelectedItem();

            mCaptureExtension = new CaptureExtension.Builder()
                    .setContext(this)
                    .setClientHandle(mCaptureClient.getHandle())
                    .setExtensionScope(scope)
                    .setListener(mListener)
                    .build();
            mCaptureExtension.start();
            updateSocketCamStatusButton(true);
        } else {
            Log.d(TAG, "Cannot start extension. Capture client is not initialized");
            binding.switchSocketcam.setChecked(false);
        }
    }

    private void stopSocketCamExtension() {
        if(mCaptureExtension != null) {
            mCaptureExtension.stop();
            updateSocketCamStatusButton(false);
        } else {
            Log.d(TAG, "Cannot stop extension. Capture extension is not initialized");
        }
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onScan(DataEvent event) {
        Log.d(TAG, "onScan : event = [" + event + "]");
        DecodedData data = event.getData();
        if (data.result == DecodedData.RESULT_SUCCESS) {
            print(event.getData().getString(), event.getDevice().getDeviceName());
        } else {
            Log.d(TAG, "Scan Cancelled");
        }
    }

    /**
     * There is no need to explicitly call EventBus.register() in each activity.
     * Capture is initialized in {@link HelloCaptureApplication} and automatically
     * registers each activity with the EventBus when it resumes and unregisters
     * it when it pauses.
     *
     * Any activity with @Subscribe methods will receive Capture events while
     * it is in the foreground.
     *
     * Subscribe to {@link ConnectionStateEvent} to monitor the Capture Service
     * connection state. The service must be in the READY state before making
     * any Capture API calls. This event provides the CaptureClient instance
     * needed to make these calls.
     */
    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    public void onCaptureServiceConnectionStateChange(ConnectionStateEvent event) {
        Log.d(TAG, "onService() called with: event = [" + event.getState().intValue() + "]");
        if (socketSnack != null) {
            socketSnack.dismiss();
        }

        TextView tv = binding.mainServiceStatus;

        ConnectionState state = event.getState();
        mCaptureClient = event.getClient();

        if (state.hasError()) {
            setText(tv, COLOR_ERROR, getString(R.string.status_error));

            CaptureError error = state.getError();
            Log.d(TAG, "onCaptureServiceConnectionStateChange error : " + error.getCode());

            if (error.getCode() == CaptureError.COMPANION_NOT_INSTALLED) {
                // Prompt to install
                socketSnack = Snackbar.make(binding.mainContent,
                        R.string.companion_not_installed,
                        Snackbar.LENGTH_INDEFINITE);
                socketSnack.setAction(R.string.action_install, v -> {
                    // Install
                    Capture.installCompanion(v.getContext());
                    socketSnack.dismiss();
                });
                socketSnack.show();
            }else if (error.getCode() == CaptureError.COMPANION_NOT_UPGRADED){
                setText(tv, COLOR_ERROR, getString(R.string.status_disconnected));
                socketSnack = Snackbar.make(binding.mainContent,
                        R.string.companion_not_upgraded,
                        Snackbar.LENGTH_INDEFINITE);
                socketSnack.setAction(R.string.action_install, v -> {
                    Capture.installCompanion(v.getContext());
                    socketSnack.dismiss();
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
                        socketSnack = Snackbar.make(binding.mainContent,
                                R.string.companion_restart,
                                Snackbar.LENGTH_INDEFINITE);
                        socketSnack.show();
                    }
                }
            } else if (error.getCode() == CaptureError.BLUETOOTH_NOT_ENABLED) {
                socketSnack = Snackbar.make(binding.mainContent,
                        R.string.companion_bluetooth_off,
                        Snackbar.LENGTH_INDEFINITE);
                socketSnack.setAction(R.string.action_dismiss, v -> socketSnack.dismiss());
                socketSnack.show();
            } else if(error.getCode() == CaptureError.ESKT_BLUETOOTHPERMISSIONMISSING) {
                // Companion, not this app, holds the Bluetooth permission. Send the
                // user to Companion's App Info page to grant "Nearby devices".
                btPermissionSnack = Snackbar.make(binding.mainContent,
                        R.string.companion_bluetooth_permission,
                        Snackbar.LENGTH_INDEFINITE);
                btPermissionSnack.setAction(R.string.action_settings, v -> openCompanionAppInfo());
                btPermissionSnack.show();
            } else {
                socketSnack = Snackbar.make(binding.mainContent, error.getMessage(),
                        Snackbar.LENGTH_INDEFINITE);
                socketSnack.show();
            }
        } else {
            // Connection has been established. You will be notified when a device is connected and ready.
            // do something or do nothing
            Log.d(TAG, "onCaptureServiceConnectionStateChange setting state " +  state.intValue());
            switch (state.intValue()) {
                case ConnectionState.CONNECTING:
                    setText(tv, COLOR_PENDING, getString(R.string.status_connecting));
                    break;
                case ConnectionState.CONNECTED:
                    setText(tv, COLOR_PENDING, getString(R.string.status_connected));
                    break;
                case ConnectionState.READY:
                    if(btPermissionSnack != null) {
                        btPermissionSnack.dismiss();
                    }
                    setText(tv, COLOR_READY, getString(R.string.status_ready));
                    // Rebuild the device list after an activity recreation: sticky events
                    // only replay the most recent DeviceStateEvent
                    for (DeviceClient device : mCaptureClient.getDeviceClientList()) {
                        String deviceGuid = device.getDeviceGuid();
                        if (!mDeviceMap.containsKey(deviceGuid)) {
                            deviceList.add(device);
                            mDeviceMap.put(deviceGuid, device);
                        }
                    }
                    deviceListAdapter.notifyDataSetChanged();
                    break;
                case ConnectionState.DISCONNECTING:
                    setText(tv, COLOR_PENDING, getString(R.string.status_disconnecting));
                    break;
                case ConnectionState.DISCONNECTED:
                    setText(tv, COLOR_IDLE, getString(R.string.status_disconnected));
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    public void onCaptureDeviceStateChange(DeviceStateEvent event) {
        DeviceClient device = event.getDevice();
        DeviceState state = event.getState();

        Log.d(TAG, "type : " + device.getDeviceType() + " guid : " + device.getDeviceGuid());
        Log.d(TAG, "name : " + device.getDeviceName() + " state : " + state.toString());

        String deviceGuid = device.getDeviceGuid();

        if (state.intValue() == DeviceState.GONE) {
            DeviceClient knownDevice = mDeviceMap.remove(deviceGuid);
            mDeviceStates.remove(deviceGuid);
            if (knownDevice != null) {
                deviceList.remove(knownDevice);
            }
        } else {
            if (!mDeviceMap.containsKey(deviceGuid)) {
                deviceList.add(device);
            }
            mDeviceMap.put(deviceGuid, device);
            mDeviceStates.put(deviceGuid, state);
        }
        deviceListAdapter.notifyDataSetChanged();
    }

    private void print(String message, String deviceName) {
        binding.helloScan.append("\n" + ++mDataCount + ". " + message);

        if (startTime != 0) {
            long timeTaken = System.currentTimeMillis() - startTime;
            binding.helloScan.append("\n" + "  Time to scan : " + timeTaken + " ms");
            startTime = 0;
        }

        mDecodedDataCount++;
        updateScanCounter();
        Log.d(TAG, String.format("Decoded Data => Decoded Data Count / Trigger Count: %d / %d", mDecodedDataCount,mTriggerCount));
    }

    private void updateScanCounter() {
        binding.scanCounter.setText(getString(R.string.scan_counter_format, mDecodedDataCount, mTriggerCount));
    }

    private void setText(TextView textView, int color, String text) {
        textView.setText(text);
        textView.setTextColor(color);
    }

    private void openCompanionAppInfo() {
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:com.socketmobile.companion")));
    }

    PropertyCallback mGetPropertyCallback = (error, property) -> runOnUiThread(() -> {
        if (error != null) {
            String err = getString(R.string.get_property_error, "" + error.getCode());
            Toast.makeText(getApplicationContext(), err, Toast.LENGTH_SHORT).show();
        } else {
            switch (property.id) {
                case Property.SOCKETCAM_STATUS:
                    if(property.byte_ != null) {
                        binding.socketcamCurrentStatusText.setText(property.byte_ + " " + SocketCamStatus.getString(property.byte_));
                    } else {
                        binding.socketcamCurrentStatusText.setText(R.string.status_unknown);
                    }
                    break;
                default:
                    //Nothing to do here
                    break;
            }
        }
    });

    PropertyCallback mSetPropertyCallback = (error, property) -> runOnUiThread(() -> {
        if (error != null) {
            String err = getString(R.string.set_property_error, "" + error.getCode());
            Toast.makeText(getApplicationContext(), err, Toast.LENGTH_SHORT).show();
        } else {
            switch (property.id) {
                case Property.SOCKETCAM_STATUS:
                    mCaptureClient.getSocketCamStatus(mGetPropertyCallback);
                    break;
                default:
                    //Nothing to do here
                    break;
            }
        }
    });

    // Events related to capture extension for using socketcam will be sent here
    CaptureExtension.Listener mListener = new CaptureExtension.Listener() {
        @Override
        public void onExtensionStateChanged(ConnectionState connectionState) {
            Log.d(TAG, "onExtensionStarted");
            switch (connectionState.intValue()) {
                case ConnectionState.READY:
                    Log.d(TAG, "Extension Started");
                    if(mCaptureClient != null ) {
                        mCaptureClient.setSocketCamStatus(SocketCamStatus.ENABLE, (captureError, property) -> runOnUiThread(() -> {
                            if (captureError != null) {
                                Toast.makeText(getApplicationContext(),
                                        getString(R.string.error_starting_camera, captureError.getMessage()),
                                        Toast.LENGTH_SHORT).show();
                            }
                        }));
                    } else {
                        Toast.makeText(getApplicationContext(), R.string.error_client_closed, Toast.LENGTH_SHORT).show();
                    }
                    break;
                case ConnectionState.DISCONNECTED:
                    Log.d(TAG, "Extension Stopped");
                    break;
                default:
                    break;
            }
        }

        @Override
        public void onError(CaptureError error) {
            Log.e(TAG, "SocketCam: onError " + error);
            Toast.makeText(getApplicationContext(),
                    getString(R.string.error_starting_extension, error.getMessage()),
                    Toast.LENGTH_SHORT).show();
        }
    };

    //region SocketCam Status buttons
    private View[] btnSocketCamStatusArray;

    private void wireSocketCamStatusButtons() {
        btnSocketCamStatusArray = new View[]{
                binding.btnSocketcamStatusEnable, binding.btnSocketcamStatusDisable,
                binding.btnSocketcamStatusNotSupported, binding.btnSocketcamStatusSupported,
                binding.btnSocketcamStatusRead};

        binding.btnSocketcamStatusEnable.setOnClickListener(this::updateSocketCamStatus);
        binding.btnSocketcamStatusDisable.setOnClickListener(this::updateSocketCamStatus);
        binding.btnSocketcamStatusSupported.setOnClickListener(this::updateSocketCamStatus);
        binding.btnSocketcamStatusNotSupported.setOnClickListener(this::updateSocketCamStatus);

        binding.btnSocketcamStatusRead.setOnClickListener(v -> mCaptureClient.getSocketCamStatus(mGetPropertyCallback));
    }

    private void updateSocketCamStatusButton(boolean enabled) {
        Log.e(TAG, "updateSocketCamStatusButtons : " + enabled);
        if (!enabled) {
            binding.socketcamCurrentStatusText.setText("");
        }

        for (View btn : btnSocketCamStatusArray) {
            btn.setEnabled(enabled);
        }
    }
    //endregion

    //region clear data
    public void clearScanData(View view) {
        mDataCount = 0;
        mDecodedDataCount = 0;
        mTriggerCount = 0;

        binding.helloScan.setText(R.string.socket_cam_scan_data);
        updateScanCounter();
    }
    //endregion

    //region DeviceListItem event
    DeviceListAdapter.DeviceItemEventListener deviceItemEventListener = new DeviceListAdapter.DeviceItemEventListener() {

        @Override
        public void onTriggerClicked(String deviceGuid, boolean isContinuousMode) {
            DeviceClient currentDevice = mDeviceMap.get(deviceGuid);

            startTime = System.currentTimeMillis();

            Log.d(TAG, "SocketCam: setScanTrigger");
            if(currentDevice == null) {
                Toast.makeText(getApplicationContext(), R.string.socketcam_device_not_found, Toast.LENGTH_SHORT).show();
                return;
            }

            if (isContinuousMode) {
                currentDevice.triggerContinuous(mSetPropertyCallback);
            } else {
                currentDevice.trigger(mSetPropertyCallback);
            }

            mTriggerCount++;
            updateScanCounter();
            Log.d(TAG, String.format("Trigger => Decoded Data Count / Trigger Count: %d / %d", mDecodedDataCount,mTriggerCount));
        }
    };
    //endregion
}
