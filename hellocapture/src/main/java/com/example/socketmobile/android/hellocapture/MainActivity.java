package com.example.socketmobile.android.hellocapture;

import android.graphics.Color;
import android.icu.util.Calendar;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
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
    private Snackbar btPermissionSnack;
    private CaptureClient mCaptureClient;
    private CaptureExtension mCaptureExtension;

    private long startTime = 0;
    private long endTime = 0;

    // Map : DeviceName -> Device
    public Map<String, DeviceClient> mDeviceMap = new HashMap<>();

    // Map : DeviceName -> DeviceState
    private Map<String, DeviceState> mDeviceStates = new HashMap<>();

    private ArrayList<DeviceClient> deviceList = new ArrayList<>();
    private DeviceListAdapter deviceListAdapter;
    private SwitchCompat switchSocketCam;

    private static final String EXPORT_FILE_NAME = "HelloCaptureData.csv";
    private int mTriggerCount;
    private int mDecodedDataCount;
    private int mDataCount = 0;

    private TextView mScanDataView;
    private Button mClearDataButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setTitle(getResources().getString(R.string.app_name) + " " + BuildConfig.VERSION_NAME);

        mTriggerCount = 0;
        mDecodedDataCount = 0;
        TextView tv = findViewById(R.id.scan_counter);
        tv.setText(String.format("%d / %d", mDecodedDataCount, mTriggerCount));


        switchSocketCam = findViewById(R.id.switch_socketcam);
        switchSocketCam.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked)
                    startSocketCamExtension();
                else
                    stopSocketCamExtension();
            }
        });

        ArrayList scopeList = new ArrayList();
        scopeList.add(ExtensionScope.LOCAL);
        scopeList.add(ExtensionScope.GLOBAL);

        Spinner spinner = findViewById(R.id.spinner_scope);
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

        mScanDataView = findViewById(R.id.hello_scan);

        mClearDataButton = findViewById(R.id.btn_clear_data);
        mClearDataButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearScanData(v);
            }
        });
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCaptureExtension != null)
            mCaptureExtension.stop();
    }

    public void updateSocketCamStatus(View view) {
        if(mCaptureClient == null || mCaptureExtension == null) {
            Toast.makeText(getApplicationContext(), "SocketCam client is not initialized", Toast.LENGTH_SHORT).show();
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
            mCaptureClient.getCaptureVersion(new PropertyCallback() {
                @Override
                public void onComplete(CaptureError captureError, Property property) {
                    if(property != null) {
                        Log.d(TAG, "SocketCam: Companion Service version : " + property.version);
                    } else {
                        Log.d(TAG, "SocketCam: Companion Service version error : " + captureError.getCode());
                    }
                }
            });

            Log.d(TAG, "SocketCam: start extension");

            Spinner spinner = (Spinner) findViewById(R.id.spinner_scope);
            ExtensionScope scope = (ExtensionScope)spinner.getSelectedItem();

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
            switchSocketCam.setChecked(false);
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
            Log.d(TAG, "onCaptureServiceConnectionStateChange error : " + error.getCode());

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
            }else if (error.getCode() == CaptureError.COMPANION_NOT_UPGRADED){
                setText(tv, COLOR_ERROR, "Disconnected");
                socketSnack = Snackbar.make(findViewById(R.id.main_content),
                        "Socket Mobile Companion must be upgraded to use your scanner",
                        Snackbar.LENGTH_INDEFINITE);
                socketSnack.setAction("Install", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
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
                        "Please ensure Companion has Bluetooth permission and Bluetooth Radio is on.",
                        Snackbar.LENGTH_INDEFINITE);
                socketSnack.setAction("Dismiss", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        socketSnack.dismiss();
                    }
                });
                socketSnack.show();
            } else if(error.getCode() == CaptureError.ESKT_BLUETOOTHPERMISSIONMISSING) {
                btPermissionSnack = Snackbar.make(findViewById(R.id.main_content),
                        "Companion does not have bluetooth permission",
                        Snackbar.LENGTH_INDEFINITE);
                btPermissionSnack.setAction("Dismiss", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        btPermissionSnack.dismiss(); // TODO : add code to call companion permission activity
                    }
                });
                btPermissionSnack.show();
            } else {
                socketSnack = Snackbar.make(findViewById(R.id.main_content), error.getMessage(),
                        Snackbar.LENGTH_INDEFINITE);
                socketSnack.show();
            }
        } else {
            // Connection has been established. You will be notified when a device is connected and ready.
            // do something or do nothing
            Log.d(TAG, "onCaptureServiceConnectionStateChange setting state " +  state.intValue());
            switch (state.intValue()) {
                case ConnectionState.CONNECTING:
                    setText(tv, COLOR_PENDING, "Connecting");
                    break;
                case ConnectionState.CONNECTED:
                    setText(tv, COLOR_PENDING, "Connected");
                    break;
                case ConnectionState.READY:
                    if(btPermissionSnack != null) {
                        btPermissionSnack.dismiss();
                    }
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

        Log.d(TAG, "type : " + device.getDeviceType() + " guid : " + device.getDeviceGuid());
        Log.d(TAG, "name : " + device.getDeviceName() + " state : " + state.toString());

        String deviceName = device.getDeviceName();

        if (!mDeviceMap.containsKey(deviceName)) {
            deviceList.add(device);
        }

        mDeviceMap.put(deviceName, device);
        mDeviceStates.put(deviceName, state);
        deviceListAdapter.notifyDataSetChanged();

    }

    private void print(String message, String deviceName) {
        mScanDataView.append("\n" + ++mDataCount + ". " + message);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            endTime = Calendar.getInstance().getTimeInMillis();
        }
        long timeTaken = endTime - startTime;
        mScanDataView.append("\n" + "  Time to scan : " + timeTaken + " ms");

        mDecodedDataCount++;
        TextView tv = findViewById(R.id.scan_counter);
        tv.setText(String.format("%d / %d", mDecodedDataCount, mTriggerCount));
        Log.d(TAG, String.format("Decoded Data => Decoded Data Count / Trigger Count: %d / %d", mDecodedDataCount,mTriggerCount));
    }

    private void setText(TextView textView, int color, String text) {
        textView.setText(text);
        textView.setTextColor(color);
    }

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

    // Events related to capture extension for using socketcam will be sent here
    CaptureExtension.Listener mListener = new CaptureExtension.Listener() {
        @Override
        public void onExtensionStateChanged(ConnectionState connectionState) {
            Log.d(TAG, "onExtensionStarted");
            switch (connectionState.intValue()) {
                case ConnectionState.READY:
                    Log.d(TAG, "Extension Started");
                    if(mCaptureClient != null ) {
                        mCaptureClient.setSocketCamStatus(SocketCamStatus.ENABLE, new PropertyCallback() {
                            @Override
                            public void onComplete(CaptureError captureError, Property property) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (captureError != null) {
                                            Toast.makeText(getApplicationContext(), "Erro starting camera scanning : " + captureError.getMessage(), Toast.LENGTH_SHORT).show();
                                        }
                                    }
                                });
                            }
                        });
                    } else {
                        Toast.makeText(getApplicationContext(), "Error: Client is closed", Toast.LENGTH_SHORT).show();
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
            Toast.makeText(getApplicationContext(), "Error starting extension : " + error.getMessage(), Toast.LENGTH_SHORT).show();
        }
    };

    //region SocketCam Status buttons
    private View btnSocketCamStatusArray[];

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
        mDataCount = 0;
        mDecodedDataCount = 0;
        mTriggerCount = 0;

        mScanDataView.setText(R.string.socket_cam_scan_data);
        TextView tv = (TextView) findViewById(R.id.scan_counter);
        tv.setText(String.format("%d / %d", mDecodedDataCount, mTriggerCount));
    }
    //endregion

    //region DeviceListItem event
    DeviceListAdapter.DeviceItemEventListener deviceItemEventListener = new DeviceListAdapter.DeviceItemEventListener() {

        @Override
        public void onSettingClicked(String deviceGUID) {

        }

        @RequiresApi(api = Build.VERSION_CODES.N)
        @Override
        public void onTriggerClicked(String deviceName, boolean isContinuousMode) {
            DeviceClient currentDevice = mDeviceMap.get(deviceName);

            startTime = Calendar.getInstance().getTimeInMillis();

            Log.d(TAG, "SocketCam: setScanTrigger");
            if(currentDevice == null) {
                Toast.makeText(getApplicationContext(), "SocketCam device not found", Toast.LENGTH_SHORT).show();
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