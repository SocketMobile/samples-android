package com.example.socketmobile.android.hellocaptureble;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.socketmobile.android.hellocaptureble.databinding.ActivityMainBinding;
import com.google.android.material.snackbar.Snackbar;
import com.socketmobile.capture.CaptureError;
import com.socketmobile.capture.android.Capture;
import com.socketmobile.capture.android.events.ConnectionStateEvent;
import com.socketmobile.capture.client.CaptureClient;
import com.socketmobile.capture.client.ConnectionState;
import com.socketmobile.capture.client.DataEvent;
import com.socketmobile.capture.client.DeviceClient;
import com.socketmobile.capture.client.DeviceDiscoveryCompleteEvent;
import com.socketmobile.capture.client.DeviceState;
import com.socketmobile.capture.client.DeviceStateEvent;
import com.socketmobile.capture.client.DeviceDiscoveryEvent;
import com.socketmobile.capture.client.callbacks.PropertyCallback;
import com.socketmobile.capture.types.DecodedData;
import com.socketmobile.capture.types.DiscoveredDevice;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static com.socketmobile.DeviceTheme.THEME_ACCESS;
import static com.socketmobile.DeviceTheme.THEME_DEFAULT;
import static com.socketmobile.DeviceTheme.THEME_HEALTH;
import static com.socketmobile.DeviceTheme.THEME_MEMBER;
import static com.socketmobile.DeviceTheme.THEME_VALUE;
import static com.socketmobile.capture.BluetoothDiscoveryMode.BLUETOOTH_LOW_ENERGY;

public class MainActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener {

    private static final String TAG = MainActivity.class.getName();
    private static final String STATE_SCAN_DATA = "scanData";

    private static final int COLOR_ERROR = Color.parseColor("#C72C30");
    private static final int COLOR_PENDING = Color.parseColor("#FF7400");
    private static final int COLOR_READY = Color.parseColor("#2AA324");
    private static final int COLOR_IDLE = Color.parseColor("#CCCCCC");

    private ActivityMainBinding binding;
    private ArrayList<DeviceClient> mDeviceClientList;
    // guid -> latest DeviceState, so each list row can show its own device's status
    private final Map<String, Integer> deviceStates = new HashMap<>();
    private DeviceListAdapter mDeviceListAdapter;
    private Snackbar socketSnack;
    private Snackbar btPermissionSnack;
    private CaptureClient mCaptureClient;

    // Discovery normally ends when the service posts a DeviceDiscoveryCompleteEvent.
    // If that event never arrives (e.g. the BLE scan could not start), this backstop
    // restores the button so it is never left disabled with no feedback.
    private static final long DISCOVERY_TIMEOUT_MS = 90_000L;
    private final Handler discoveryHandler = new Handler(Looper.getMainLooper());
    private final Runnable discoveryTimeout = () -> finishDiscovery(true);
    private int discoveredThisRun;

    // Devices found by discovery. The spinner shows a display label per
    // row; discoveredDeviceIds holds the unique identifier for the same row
    // ("" for the placeholder), so no parsing of the label is ever needed.
    private ArrayList<String> discoveredDeviceIds;
    private ArrayAdapter<String> discoveredDevicesAdapter;

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

        /** Start Capture **/
        Capture.builder(getApplicationContext())
                .enableLogging(BuildConfig.DEBUG)
                .build();

        resetDiscoveredDevicesSpinner();

        setTitle(getString(R.string.app_name) + " " + BuildConfig.VERSION_NAME);

        mDeviceClientList = new ArrayList<>();
        mDeviceListAdapter = new DeviceListAdapter(mDeviceClientList, deviceStates, deviceItemEventListener);
        binding.deviceList.setLayoutManager(new LinearLayoutManager(this));
        binding.deviceList.setAdapter(mDeviceListAdapter);

        binding.discovery.setOnClickListener(v -> startDiscovery());
        binding.btnClearData.setOnClickListener(v -> clearData());

        binding.helloScan.setText(R.string.label_scan_data);
        if (savedInstanceState != null) {
            CharSequence scanData = savedInstanceState.getCharSequence(STATE_SCAN_DATA);
            if (scanData != null) {
                binding.helloScan.setText(scanData);
            }
        }
        binding.helloScan.setMovementMethod(new ScrollingMovementMethod());
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putCharSequence(STATE_SCAN_DATA, binding.helloScan.getText());
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop() called");
        // Abandon any in-flight discovery so the timeout can't fire after we stop
        discoveryHandler.removeCallbacks(discoveryTimeout);
        mDeviceClientList.clear();
        deviceStates.clear();
        mDeviceListAdapter.notifyDataSetChanged();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onError(CaptureError error) {
        Log.d(TAG, "onError : errorCode = " + error.getCode());
    }

    /** Subscribe to DataEvent to receive data when a barcode or a tag is scanned. **/
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onScan(DataEvent event) {
        Log.d(TAG, "onScan : event = [" + event + "]");
        DecodedData data = event.getData();
        print(data.getString(), event.getData().data);
    }

    /**
     * There is no need to explicitly call EventBus.register() in each activity.
     * Capture is initialized in {@link #onCreate} and automatically
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
     *
     * Service can be in one the following state:
     * CONNECTING, CONNECTED, READY, DISCONNECTING, DISCONNECTED
     */
    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    public void onCaptureServiceConnectionStateChange(ConnectionStateEvent event) {
        Log.d(TAG, "onService() called with: event = [" + event + "]");
        if (socketSnack != null) {
            socketSnack.dismiss();
        }

        TextView tv = binding.mainServiceStatus;

        ConnectionState state = event.getState();
        mCaptureClient = event.getClient();

        if (state.hasError()) {
            Log.d(TAG, "CaptureServiceConnectionStateChange with error : " + state.getError());
            setText(tv, COLOR_ERROR, getString(R.string.status_error));

            CaptureError error = state.getError();
            if (error.getCode() == CaptureError.COMPANION_NOT_INSTALLED) {
                // Prompt to install
                socketSnack = Snackbar.make(binding.getRoot(),
                        R.string.companion_not_installed,
                        Snackbar.LENGTH_INDEFINITE);
                socketSnack.setAction(R.string.action_install, v -> {
                    // Install
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
                        socketSnack = Snackbar.make(binding.getRoot(),
                                R.string.companion_restart,
                                Snackbar.LENGTH_INDEFINITE);
                        socketSnack.show();
                    }
                }
            } else if (error.getCode() == CaptureError.BLUETOOTH_NOT_ENABLED) {
                socketSnack = Snackbar.make(binding.getRoot(),
                        R.string.bluetooth_off,
                        Snackbar.LENGTH_INDEFINITE);
                socketSnack.setAction(R.string.action_dismiss, v -> socketSnack.dismiss());
                socketSnack.show();
            } else if (error.getCode() == CaptureError.ESKT_BLUETOOTHPERMISSIONMISSING) {
                // Companion, not this app, holds the Bluetooth permission. Send the
                // user to Companion's App Info page to grant "Nearby devices".
                btPermissionSnack = Snackbar.make(binding.getRoot(),
                        R.string.companion_bluetooth_permission,
                        Snackbar.LENGTH_INDEFINITE);
                btPermissionSnack.setAction(R.string.action_settings, v -> openCompanionAppInfo());
                btPermissionSnack.show();
            } else {
                socketSnack = Snackbar.make(binding.getRoot(), error.getMessage(),
                        Snackbar.LENGTH_INDEFINITE);
                socketSnack.show();
            }
        } else {
            // Connection has been established. You will be notified when a device is connected and ready.
            // do something or do nothing
            switch (state.intValue()) {
                case ConnectionState.CONNECTING:
                    setText(tv, COLOR_PENDING, getString(R.string.status_connecting));
                    break;
                case ConnectionState.CONNECTED:
                    setText(tv, COLOR_PENDING, getString(R.string.status_connected));
                    break;
                case ConnectionState.READY:
                    if (btPermissionSnack != null) {
                        btPermissionSnack.dismiss();
                    }
                    setText(tv, COLOR_READY, getString(R.string.status_ready));
                    // Rebuild the device list after an activity recreation: sticky events
                    // only replay the most recent DeviceStateEvent
                    for (DeviceClient device : mCaptureClient.getDeviceClientList()) {
                        if (findListedDevice(device.getDeviceGuid()) == null) {
                            mDeviceClientList.add(device);
                            // No DeviceStateEvent in hand here; a connected device is READY,
                            // and the sticky DeviceStateEvent will correct it if needed
                            deviceStates.put(device.getDeviceGuid(), DeviceState.READY);
                        }
                    }
                    mDeviceListAdapter.notifyDataSetChanged();
                    break;
                case ConnectionState.DISCONNECTING:
                    setText(tv, COLOR_PENDING, getString(R.string.status_disconnecting));
                    break;
                case ConnectionState.DISCONNECTED:
                    setText(tv, COLOR_IDLE, getString(R.string.status_disconnected));
            }
        }
    }

    /** DeviceStateEvent is sent everytime a Socket Mobile device connects or disconnects
     */
    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    public void onCaptureDeviceStateChange(DeviceStateEvent event) {
        DeviceClient device = event.getDevice();
        DeviceState state = event.getState();
        Log.d(TAG, "device : type : " + device.getDeviceType() + " guid : " + device.getDeviceGuid());
        Log.d(TAG, "device : name : " + device.getDeviceName() + " state: " + state.intValue());

        // The same physical device can be seen through different DeviceClient
        // instances (sticky event replay vs getDeviceClientList()), so always
        // match list entries by device GUID, never by instance
        DeviceClient listedDevice = findListedDevice(device.getDeviceGuid());
        switch (state.intValue()) {
            case DeviceState.GONE:
                if (listedDevice != null) {
                    mDeviceClientList.remove(listedDevice);
                }
                deviceStates.remove(device.getDeviceGuid());
                break;

            case DeviceState.READY:
                if (listedDevice == null) {
                    mDeviceClientList.add(device);
                }
                deviceStates.put(device.getDeviceGuid(), state.intValue());
                break;

            default:
                // Other states (e.g. OPEN) only update the row's status, if listed
                if (listedDevice != null) {
                    deviceStates.put(device.getDeviceGuid(), state.intValue());
                }
                break;
        }
        // Each row renders its own device's status from deviceStates
        mDeviceListAdapter.notifyDataSetChanged();
    }

    private DeviceClient findListedDevice(String deviceGuid) {
        for (DeviceClient device : mDeviceClientList) {
            if (device.getDeviceGuid().equals(deviceGuid)) {
                return device;
            }
        }
        return null;
    }

    /** DeviceDiscoveryEvent is sent everytime a Socket Mobile Bluetooth LE device is discovered.
     * DeviceDiscoveryEvent is sent as a result of initiating a discovery
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onCaptureDeviceDiscoveryChange(DeviceDiscoveryEvent event) {
        Log.d(TAG, "DeviceDiscoveryEvent received ");
        DiscoveredDevice device = event.getDiscoveredDevice();
        if(device != null) {
            Log.d(TAG, "name " + device.getName());
            Log.d(TAG, "unique id " + device.getUniqueIdentifier());

            discoveredThisRun++;
            addDiscoveredDevice(device);
        }
        // A device turned up, so discovery is clearly working: restore the button now.
        finishDiscovery(false);
    }

    /**  DeviceDiscoveryCompleteEvent is sent to notify completion of a discovery
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onCaptureDeviceDiscoveryChange(DeviceDiscoveryCompleteEvent event) {
        if(event.isComplete()) {
            Log.d(TAG, "DeviceDiscoveryCompleteEvent is complete");
        }
        // Discovery finished; if nothing turned up, say so instead of leaving a blank spinner.
        finishDiscovery(true);
    }

    private void enableDiscoveryButton(boolean enable) {
        binding.discovery.setEnabled(enable);
    }

    /** Restore the discovery button and cancel the safety timeout once discovery
     *  ends. When the window completes without finding anything, tell the user so
     *  an empty spinner is not mistaken for a broken button. */
    private void finishDiscovery(boolean announceIfEmpty) {
        discoveryHandler.removeCallbacks(discoveryTimeout);
        enableDiscoveryButton(true);
        binding.discovery.setText(R.string.action_add_device);
        if (announceIfEmpty && discoveredThisRun == 0) {
            Toast.makeText(this, R.string.discovery_no_devices, Toast.LENGTH_LONG).show();
        }
    }

    private void openCompanionAppInfo() {
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:com.socketmobile.companion")));
    }

    private void print(String message, byte[] data) {
        binding.helloScan.append("\n" + message);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < data.length; i++) {
            sb.append(data[i]);
            if (i < data.length - 1) {
                sb.append(", ");
            }
        }
        binding.helloScan.append("\n\n" + sb);
        Log.d(TAG, "print : data : " + sb);
    }

    private void clearData() {
        binding.helloScan.setText(R.string.label_scan_data);
    }

    private void setText(TextView textView, int color, String text) {
        textView.setText(text);
        textView.setTextColor(color);
    }

    private void showOperationResult(String operation, CaptureError captureError) {
        runOnUiThread(() -> {
            if (captureError != null) {
                Toast.makeText(MainActivity.this,
                        getString(R.string.op_failed, operation, captureError.getCode(), captureError.getMessage()),
                        Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(MainActivity.this,
                        getString(R.string.op_completed, operation),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
        String uniqueId = discoveredDeviceIds.get(position);
        if (uniqueId.isEmpty()) {
            // placeholder row
            return;
        }
        if(mCaptureClient == null) {
            Toast.makeText(getApplicationContext(), R.string.service_not_ready, Toast.LENGTH_SHORT).show();
            return;
        }
        mCaptureClient.connectDevice(uniqueId, (captureError, property) -> {
            if (captureError != null) {
                showOperationResult("connectDevice", captureError);
            } else {
                Log.d(TAG, "connectDevice Completed");
            }
        });
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }

    public void resetDiscoveredDevicesSpinner() {
        discoveredDeviceIds = new ArrayList<>();
        discoveredDeviceIds.add("");

        discoveredDevicesAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item);
        discoveredDevicesAdapter.add(getString(R.string.select_an_option));
        discoveredDevicesAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerDiscoveredDevices.setAdapter(discoveredDevicesAdapter);
        binding.spinnerDiscoveredDevices.setOnItemSelectedListener(this);
    }

    private void addDiscoveredDevice(DiscoveredDevice device) {
        if(discoveredDeviceIds.contains(device.getUniqueIdentifier())) return;
        discoveredDeviceIds.add(device.getUniqueIdentifier());
        // add() goes through the adapter so the spinner is refreshed
        discoveredDevicesAdapter.add(device.getName() + " (" + device.getUniqueIdentifier() + ")");
    }

    PropertyCallback triggerPropertyCallback = (captureError, property) ->
            showOperationResult("Set Trigger", captureError);

    PropertyCallback themePropertyCallback = (captureError, property) ->
            showOperationResult("Set Theme", captureError);

    /** Discovery looks for Bluetooth LE devices available in the vicinity.
     * Discovery runs for the specified duration and sends a DeviceDiscoveryEvent each time a Socket Mobile Bluetooth LE device is discovered.
     * */
    private void startDiscovery() {
        if (mCaptureClient == null) {
            Toast.makeText(getApplicationContext(), R.string.service_not_ready, Toast.LENGTH_SHORT).show();
            return;
        }
        discoveredThisRun = 0;
        enableDiscoveryButton(false);
        binding.discovery.setText(R.string.action_scanning);
        // Backstop: if no DeviceDiscoveryCompleteEvent ever arrives, recover the button anyway.
        discoveryHandler.postDelayed(discoveryTimeout, DISCOVERY_TIMEOUT_MS);
        resetDiscoveredDevicesSpinner();
        mCaptureClient.addDevice((byte) BLUETOOTH_LOW_ENERGY, (captureError, property) -> {
            if (captureError != null) {
                // Discovery could not start, so no discovery event will re-enable the button
                runOnUiThread(() -> finishDiscovery(false));
            }
            showOperationResult("addDevice", captureError);
        });
    }

    /** Device row actions: trigger and theme show a popup menu anchored on the row's button;
     * disconnect and remove act on the row's device directly. **/
    DeviceListAdapter.DeviceItemEventListener deviceItemEventListener = new DeviceListAdapter.DeviceItemEventListener() {

        @Override
        public void onTriggerClicked(DeviceClient deviceClient, View anchor) {
            PopupMenu popupMenu = new PopupMenu(MainActivity.this, anchor);
            popupMenu.getMenuInflater().inflate(R.menu.trigger_menu, popupMenu.getMenu());
            popupMenu.setOnMenuItemClickListener(menuItem -> {
                int menuId = menuItem.getItemId();
                if(menuId == R.id.menu_trigger_start) {
                    deviceClient.trigger(triggerPropertyCallback);
                } else if(menuId == R.id.menu_trigger_stop) {
                    deviceClient.triggerStop(triggerPropertyCallback);
                } else if(menuId == R.id.menu_trigger_enable) {
                    deviceClient.triggerEnable(triggerPropertyCallback);
                } else if(menuId == R.id.menu_trigger_disable) {
                    deviceClient.triggerDisable(triggerPropertyCallback);
                } else if(menuId == R.id.menu_trigger_continuous) {
                    deviceClient.triggerContinuous(triggerPropertyCallback);
                }
                return true;
            });
            popupMenu.show();
        }

        /** Theme Selection chooses the LED sequence played on S550 or S370 devices when scanning **/
        @Override
        public void onThemeClicked(DeviceClient deviceClient, View anchor) {
            PopupMenu popupMenu = new PopupMenu(MainActivity.this, anchor);
            popupMenu.getMenuInflater().inflate(R.menu.theme_menu, popupMenu.getMenu());
            popupMenu.setOnMenuItemClickListener(item -> {
                byte theme = 0;
                int menuId = item.getItemId();
                if(menuId ==  R.id.menu_theme_default) {
                    theme = THEME_DEFAULT;
                } else if(menuId ==  R.id.menu_theme_health) {
                    theme = THEME_HEALTH;
                } else if(menuId ==  R.id.menu_theme_access) {
                    theme = THEME_ACCESS;
                } else if(menuId ==  R.id.menu_theme_value) {
                    theme = THEME_VALUE;
                } else if(menuId ==  R.id.menu_theme_member) {
                    theme = THEME_MEMBER;
                }
                deviceClient.setThemeSelection(theme, themePropertyCallback);
                return true;
            });
            popupMenu.show();
        }

        @Override
        public void onDisconnectClicked(DeviceClient deviceClient) {
            mCaptureClient.disconnectDevice(deviceClient.getUniqueId(), (captureError, property) ->
                    showOperationResult("disconnectDevice", captureError));
        }

        @Override
        public void onRemoveClicked(DeviceClient deviceClient) {
            mCaptureClient.removeDevice(deviceClient.getUniqueId(), (captureError, property) ->
                    showOperationResult("removeDevice", captureError));
        }
    };
}
