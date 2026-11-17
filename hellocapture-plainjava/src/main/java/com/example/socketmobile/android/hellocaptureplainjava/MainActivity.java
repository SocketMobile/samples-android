package com.example.socketmobile.android.hellocaptureplainjava;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.socketmobile.android.hellocaptureplainjava.databinding.ActivityMainBinding;
import com.socketmobile.capture.AppKey;
import com.socketmobile.capture.CaptureError;
import com.socketmobile.capture.client.BatteryLevelEvent;
import com.socketmobile.capture.client.CaptureClient;
import com.socketmobile.capture.client.Configuration;
import com.socketmobile.capture.client.ConnectionState;
import com.socketmobile.capture.client.DataEvent;
import com.socketmobile.capture.client.DeviceClient;
import com.socketmobile.capture.client.DeviceManagerStateEvent;
import com.socketmobile.capture.client.DeviceState;
import com.socketmobile.capture.client.DeviceStateEvent;
import com.socketmobile.capture.client.PowerStateEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Demonstrates the plain Java Capture SDK (com.socketmobile:capture) used directly,
 * without the capture-android wrapper. Compared to the other samples in this project:
 *
 *  - No EventBus: every notification arrives on a single {@link CaptureClient.Listener}.
 *  - No manifest meta-data: the app key is passed programmatically via {@link AppKey}.
 *  - Devices arriving in AVAILABLE state must be opened explicitly (see
 *    {@link #openIfAvailable}); capture-android does this for you.
 *  - Listener callbacks are NOT delivered on the main thread, so every UI update
 *    below goes through runOnUiThread().
 *
 * NOTE ON THE APP KEY: a Capture app key is bound to the application id it was issued
 * for. This sample reuses hellocapture's key, which is why this module declares the
 * same applicationId (com.example.socketmobile.android.hellocapture) in build.gradle —
 * and why installing it replaces the hellocapture sample on the device.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getName();

    // hellocapture's credentials (see the note in the class comment)
    private static final String APP_ID = "android:com.example.socketmobile.android.hellocapture";
    private static final String DEVELOPER_ID = "BB57D8E1-F911-47BA-B510-693BE162686A";
    private static final String APP_KEY =
            "MC0CFBPgc4u5RXGUAddh+ZlUrriiQID0AhUAx6SisUBSdiZhyWzdVsnWm25FVTA=";

    private static final int COLOR_ERROR = Color.parseColor("#C72C30");
    private static final int COLOR_PENDING = Color.parseColor("#FF7400");
    private static final int COLOR_READY = Color.parseColor("#2AA324");
    private static final int COLOR_IDLE = Color.parseColor("#CCCCCC");

    private ActivityMainBinding binding;
    private CaptureClient mCaptureClient;

    private ArrayList<DeviceClient> mDeviceClientList;
    // guid -> latest DeviceState, so each list row can show its own device's status
    private final Map<String, Integer> deviceStates = new HashMap<>();
    private DeviceListAdapter mDeviceListAdapter;

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

        setText(binding.mainServiceStatus, COLOR_PENDING, getString(R.string.status_connecting));

        mDeviceClientList = new ArrayList<>();
        mDeviceListAdapter = new DeviceListAdapter(mDeviceClientList, deviceStates);
        binding.deviceList.setLayoutManager(new LinearLayoutManager(this));
        binding.deviceList.setAdapter(mDeviceListAdapter);

        binding.btnClearData.setOnClickListener(v -> binding.scanData.setText(""));

        /* Plain Java SDK: build the configuration with programmatic credentials
           and connect. ConnectionCallback reports the service connection state. */
        AppKey appKey = new AppKey(APP_KEY, APP_ID, DEVELOPER_ID);

        Configuration configuration = new Configuration()
                .appKey(appKey)
                .setContext(getApplicationContext())
                .enableLogging(BuildConfig.DEBUG ? Level.ALL : Level.INFO);

        mCaptureClient = new CaptureClient(configuration);
        mCaptureClient.setListener(mCaptureListener);

        /* The Capture service runs inside the Socket Mobile Companion app. The
           capture-android wrapper starts it automatically; with the plain Java SDK
           the application is responsible for it. Retry while the service boots. */
        startCompanionService();
        mCaptureClient.connect(10, state -> runOnUiThread(() -> onConnectionStateChanged(state)));
    }

    private void startCompanionService() {
        // Companion listens for this broadcast with a receiver that brings up its
        // Capture service (the same trigger it uses at boot)
        Intent intent = new Intent("com.socketmobile.capture.START_SERVICE");
        intent.setPackage("com.socketmobile.companion");
        sendBroadcast(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCaptureClient != null) {
            mCaptureClient.disconnect();
        }
    }

    private void onConnectionStateChanged(ConnectionState state) {
        TextView tv = binding.mainServiceStatus;
        if (state.hasError()) {
            CaptureError error = state.getError();
            Log.e(TAG, "connection error : " + error.getCode() + " : " + error.getMessage());
            setText(tv, COLOR_ERROR,
                    getString(R.string.error_with_code, error.getCode(), error.getMessage()));
            return;
        }
        switch (state.intValue()) {
            case ConnectionState.CONNECTING:
                setText(tv, COLOR_PENDING, getString(R.string.status_connecting));
                break;
            case ConnectionState.CONNECTED:
                setText(tv, COLOR_PENDING, getString(R.string.status_connected));
                break;
            case ConnectionState.READY:
                setText(tv, COLOR_READY, getString(R.string.status_ready));
                break;
            case ConnectionState.DISCONNECTING:
                setText(tv, COLOR_PENDING, getString(R.string.status_disconnecting));
                break;
            case ConnectionState.DISCONNECTED:
                setText(tv, COLOR_IDLE, getString(R.string.status_disconnected));
                break;
        }
    }

    /** With the plain Java SDK the application must open an AVAILABLE device itself
     *  to bring it to READY; capture-android normally does this automatically. */
    private boolean openIfAvailable(DeviceClient device, DeviceState state) {
        if (state.intValue() == DeviceState.AVAILABLE) {
            device.open();
            return true;
        }
        return false;
    }

    private DeviceClient findListedDevice(String guid) {
        for (DeviceClient device : mDeviceClientList) {
            if (device.getDeviceGuid().equals(guid)) {
                return device;
            }
        }
        return null;
    }

    /** Maintain the connected-device list and each device's status, like the other
     *  samples: add on READY, drop on GONE, and update a listed device's status for
     *  any other state. Must run on the UI thread. */
    private void updateDeviceList(DeviceClient device, int stateValue) {
        String guid = device.getDeviceGuid();
        DeviceClient listed = findListedDevice(guid);
        if (stateValue == DeviceState.GONE) {
            if (listed != null) {
                mDeviceClientList.remove(listed);
            }
            deviceStates.remove(guid);
        } else if (stateValue == DeviceState.READY) {
            if (listed == null) {
                mDeviceClientList.add(device);
            }
            deviceStates.put(guid, stateValue);
        } else if (listed != null) {
            deviceStates.put(guid, stateValue);
        }
        mDeviceListAdapter.notifyDataSetChanged();
    }

    /** All Capture notifications arrive here — on a background thread. */
    private final CaptureClient.Listener mCaptureListener = new CaptureClient.Listener() {

        @Override
        public void onDeviceManagerStateEvent(DeviceManagerStateEvent event) {
            Log.d(TAG, "device manager state : " + event.getState().intValue());
            openIfAvailable(event.getDevice(), event.getState());
        }

        @Override
        public void onDeviceStateEvent(DeviceStateEvent event) {
            DeviceClient device = event.getDevice();
            DeviceState state = event.getState();
            Log.d(TAG, "device : " + device.getDeviceName() + " state : " + state.intValue());

            // An AVAILABLE device is opened here; a READY event will follow once it opens
            if (openIfAvailable(device, state)) {
                return;
            }
            runOnUiThread(() -> updateDeviceList(device, state.intValue()));
        }

        @Override
        public void onData(DataEvent event) {
            String decodedData = event.getData().getString();
            Log.d(TAG, "scan data : " + decodedData);
            runOnUiThread(() -> binding.scanData.append(decodedData + "\n"));
        }

        @Override
        public void onBatteryLevelChanged(BatteryLevelEvent event) {
            Log.d(TAG, "battery level changed");
        }

        @Override
        public void onPowerStateChanged(PowerStateEvent event) {
            Log.d(TAG, "power state changed");
        }

        @Override
        public void onError(CaptureError error) {
            Log.e(TAG, "capture error : " + error.getCode() + " : " + error.getMessage());
            runOnUiThread(() -> setText(binding.mainServiceStatus, COLOR_ERROR,
                    getString(R.string.error_with_code, error.getCode(), error.getMessage())));
        }
    };

    private void setText(TextView textView, int color, String text) {
        textView.setText(text);
        textView.setTextColor(color);
    }
}
