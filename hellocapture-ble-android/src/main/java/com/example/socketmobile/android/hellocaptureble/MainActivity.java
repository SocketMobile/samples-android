package com.example.socketmobile.android.hellocaptureble;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;
import com.socketmobile.capture.CaptureError;
import com.socketmobile.capture.Permissions;
import com.socketmobile.capture.Property;
import com.socketmobile.capture.android.Capture;
import com.socketmobile.capture.android.events.ConnectionStateEvent;
import com.socketmobile.capture.client.CaptureClient;
import com.socketmobile.capture.client.ConnectionState;
import com.socketmobile.capture.client.DataEvent;
import com.socketmobile.capture.client.DeviceClient;
import com.socketmobile.capture.client.DeviceDiscoveryCompleteEvent;
import com.socketmobile.capture.client.DeviceManagerStateEvent;
import com.socketmobile.capture.client.DeviceState;
import com.socketmobile.capture.client.DeviceStateEvent;
import com.socketmobile.capture.client.DeviceDiscoveryEvent;
import com.socketmobile.capture.client.callbacks.PropertyCallback;
import com.socketmobile.capture.types.DecodedData;
import com.socketmobile.capture.types.DeviceType;
import com.socketmobile.capture.types.DiscoveredDevice;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;

import static com.socketmobile.DeviceTheme.THEME_ACCESS;
import static com.socketmobile.DeviceTheme.THEME_DEFAULT;
import static com.socketmobile.DeviceTheme.THEME_HEALTH;
import static com.socketmobile.DeviceTheme.THEME_MEMBER;
import static com.socketmobile.DeviceTheme.THEME_VALUE;
import static com.socketmobile.capture.BluetoothDiscoveryMode.BLUETOOTH_CLASSIC;
import static com.socketmobile.capture.BluetoothDiscoveryMode.BLUETOOTH_LOW_ENERGY;
import static com.socketmobile.capture.Permissions.EXTRA_ERROR_CODE;
import static com.socketmobile.capture.Permissions.EXTRA_GRANT_RESULT;
import static com.socketmobile.capture.Permissions.REQUEST_CODE_LOCATION_PERMISSION_MISSING;
import static com.socketmobile.capture.Property.DEVICE_DISCONNECT;
import static com.socketmobile.capture.Property.DEVICE_LASSO_LIFE_SPAN;
import static com.socketmobile.capture.Property.DEVICE_LASSO_STATUS;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.app.ActivityCompat;

public class MainActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener {

    private static final String TAG = MainActivity.class.getName();

    private static final int COLOR_ERROR = Color.parseColor("#C72C30");
    private static final int COLOR_PENDING = Color.parseColor("#FF7400");
    private static final int COLOR_READY = Color.parseColor("#2AA324");
    private static final int COLOR_IDLE = Color.parseColor("#CCCCCC");

    private ArrayList<DeviceClient> mDeviceClientList;
    private Snackbar socketSnack;
    private CaptureClient mCaptureClient;
    private DeviceClient mDeviceClient;
    private DeviceClient mDeviceManager;

    private ArrayList<String> favorites;
    ListView mDevicelistView;
    private ArrayList<String> mDeviceList;
    private ArrayAdapter mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /** Start Capture **/
        Capture.builder(getApplicationContext())
                .enableLogging(BuildConfig.DEBUG)
                .build();

        setFavoriteSpinner();

        TextView tv = findViewById(R.id.main_device_status);
        handleColor(tv, DeviceState.GONE);

        TextView tvManager = findViewById(R.id.main_device_manager_status);
        handleColor(tvManager, DeviceState.GONE);

        setTitle(getResources().getString(R.string.app_name) + " " + BuildConfig.VERSION_NAME);

        mDeviceClientList = new ArrayList<>();
        mDeviceList = new ArrayList<>();
        mDevicelistView = (ListView) findViewById(R.id.device_list);
        mAdapter = new ArrayAdapter<String>(this,
                R.layout.device_list_item, R.id.device_name, mDeviceList);
        mDevicelistView.setAdapter(mAdapter);

        TextView scanView = ((TextView) findViewById(R.id.hello_scan));
        scanView.setText("Scan Data : ");
        scanView.setMovementMethod(new ScrollingMovementMethod());
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop() called with: " + "");
        if (mAdapter != null) mAdapter.clear();
    }



    public void nextActivity(View view) {
        Intent i = new Intent(this, SecondCaptureActivity.class);
        startActivity(i);
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

    /** Subscribe to ConnectionStateEvent to receive notification when there is a change in service state.
     * Service can be in one the following state:
     * CONNECTING, CONNECTED, READY, DISCONNECTING, DISCONNECTED **/
    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    public void onCaptureServiceConnectionStateChange(ConnectionStateEvent event) {
        Log.d(TAG, "onService() called with: event = [" + event + "]");
        if (socketSnack != null) {
            socketSnack.dismiss();
        }

        TextView tv = findViewById(R.id.main_service_status);

        ConnectionState state = event.getState();
        mCaptureClient = event.getClient();

        if (state.hasError()) {
            Log.d(TAG, "CaptureServiceConnectionStateChange with error : " + state.getError());
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
                        if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 0x10);
                            }
                            return;
                        }
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
                    setText(tv, COLOR_PENDING,"Connecting");
                    break;
                case ConnectionState.CONNECTED:
                    setText(tv, COLOR_PENDING,"Connected");
                    break;
                case ConnectionState.READY:
                    setText(tv, COLOR_READY,"Ready");
                    break;
                case ConnectionState.DISCONNECTING:
                    setText(tv, COLOR_PENDING,"Disconnecting");
                    break;
                case ConnectionState.DISCONNECTED:
                    setText(tv, COLOR_IDLE, "Disconnected");
            }
        }
    }

    /** Device Manager drives the Bluetooth LE experience.
     * Subscribe to DeviceManagerStateEvent to be notified of change in DeviceManager state.
     * When DeviceManager is in Ready state, service can connect to Bluetooth LE devices**/
    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    public void onCaptureDeviceManagerStateChange(DeviceManagerStateEvent event) {
        DeviceClient device = event.getDevice();
        DeviceState state = event.getState();
        Log.d(TAG, "devicemanager : type : " + device.getDeviceType() + " guid : " + device.getDeviceGuid());

        if(DeviceType.retrieveClassType(device.getDeviceType()) == DeviceType.ClassType.kDeviceManagerClass) {
            mDeviceManager = device;
            TextView tv = findViewById(R.id.main_device_manager_status);
            handleColor(tv, state.intValue());
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

        mDeviceClient = device;
        String type = (DeviceType.isBarcodeScanner(device.getDeviceType()))? "BARCODE" : "NFC";
        TextView tv = findViewById(R.id.main_device_status);
        handleColor(tv, state.intValue());
        switch (state.intValue()) {
            case DeviceState.GONE:
                mDeviceClientList.remove(device);
                mAdapter.remove(device.getDeviceName() + " " + type);
                mAdapter.notifyDataSetChanged();
                if(!mAdapter.isEmpty()) {
                    handleColor(tv, DeviceState.READY);
                }
                break;

            case DeviceState.READY:
                mDeviceClientList.add(device);
                mAdapter.add(device.getDeviceName() + " " + type);
                mAdapter.notifyDataSetChanged();
                break;

            default:
                break;
        }

    }

    /** DeviceDiscoveryEvent is sent everytime a Socket Mobile Bluetooth LE device is discovered.
     * DeviceDiscoveryEvent is sent as a result of initiating a manual discovery
     */
    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    public void onCaptureDeviceDiscoveryChange(DeviceDiscoveryEvent event) {
        Log.d(TAG, "DeviceDiscoveryEvent received ");
        enableManualDiscoveryButton(true);
        DiscoveredDevice device = event.getDiscoveredDevice();
        if(device != null) {
            Log.d(TAG, "name " + device.getName());
            Log.d(TAG, "unique id " + device.getUniqueIdentifier());

            updateFavoriteSpinner(device.getUniqueIdentifier(), device.getName());
        }
    }

    /**  DeviceDiscoveryCompleteEvent is sent to notify completion a manual discovery
     */
    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    public void onCaptureDeviceDiscoveryChange(DeviceDiscoveryCompleteEvent event) {
        enableManualDiscoveryButton(true);
        if(event.isComplete()) {
            Log.d(TAG, "DeviceDiscoveryCompleteEvent is complete");
        }
    }

    private void enableManualDiscoveryButton(boolean enable) {
        ((Button) findViewById(R.id.manual_discovery)).setEnabled(enable);
    }

    private void handleColor(TextView tv, int deviceState) {
        int color;
        String message;
        switch (deviceState) {
            default:
            case DeviceState.GONE:
                // Gone
                color = COLOR_IDLE;
                message = "Gone";
                break;
            case DeviceState.AVAILABLE:
                // closed
                color = COLOR_ERROR;
                message = "Closed";
                break;
            case DeviceState.OPEN:
                // no focus
                color = COLOR_PENDING;
                message = "In Use";
                break;
            case DeviceState.READY:
                // focus
                color = COLOR_READY;
                message = "Ready";
                break;
        }

        tv.setTextColor(color);
        tv.setText(message);
    }

    private void print(String message, byte[] data) {
        TextView tv = ((TextView) findViewById(R.id.hello_scan));
        tv.append("\n" + message);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < data.length; i++) {
            sb.append(data[i]);
            if (i < data.length - 1) {
                sb.append(", ");
            }
        }
        tv.append("\n\n" + sb.toString());
        Log.d(TAG, "print : data : " + sb.toString());
        tv.setMovementMethod(new ScrollingMovementMethod());
    }

    public void clearData(View view) {
        TextView tv = ((TextView) findViewById(R.id.hello_scan));
        tv.setText("Scan Data : ");
    }

    private void setText(TextView textView, int color, String text) {
        textView.setText(text);
        textView.setTextColor(color);
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
        if(mDeviceManager == null) {
            Toast.makeText(getApplicationContext(), R.string.device_manager_error, Toast.LENGTH_SHORT).show();
            return;
        }
        String selected = favorites.get(position);
        switch (selected) {
            case "Select an Option...":
                return;
            default:
                if(selected.indexOf(" ") > 0) {
                    selected = selected.substring(0, selected.indexOf(" "));
                }
                break;
        }
        mCaptureClient.connectDevice(selected, new PropertyCallback() {
            @Override
            public void onComplete(@Nullable CaptureError captureError, @Nullable Property property) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if(captureError != null) {
                            Toast.makeText(MainActivity.this, "connectDevice Failed : " + "Code : " + captureError.getCode() + " : " + captureError.getMessage(), Toast.LENGTH_SHORT).show();
                        } else {
                            Log.d(TAG, "connectDevice Completed");
                        }
                    }
                });
            }
        });
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }

    public void setFavoriteSpinner() {
        favorites = new ArrayList();
        favorites.add("Select an Option...");

        Spinner spinner = (Spinner) findViewById(R.id.spinner_favorite);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, favorites);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(this);
    }

    private void updateFavoriteSpinner(String uId, String deviceName) {
        if(favorites.contains(uId + " (" + deviceName + ")")) return;
        favorites.add(uId + " (" + deviceName + ")");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult : requestCode = " + requestCode + " : resultCode = " + resultCode);
        if(requestCode == REQUEST_CODE_LOCATION_PERMISSION_MISSING) {
            if (resultCode == Activity.RESULT_OK) {
                int result = data.getIntExtra(EXTRA_GRANT_RESULT, PackageManager.PERMISSION_DENIED);
                if (result == PackageManager.PERMISSION_DENIED) {
                    // TODO: service cannot handle the request since the permission was not granted
                    Toast.makeText(getApplicationContext(), R.string.location_permission_denied, Toast.LENGTH_SHORT).show();
                } else {
                    // TODO : make the service call again to complete the request
                    Toast.makeText(getApplicationContext(), R.string.location_permission_granted, Toast.LENGTH_SHORT).show();
                }
            }
        }
        if (resultCode == Activity.RESULT_CANCELED) {
            // TODO: Write your code if there's no result
        }
    }

    PropertyCallback propertyCallback = new PropertyCallback(){

        @Override
        public void onComplete(final CaptureError captureError, final Property property) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (captureError != null) {
                        Log.d(TAG, "set propetty complete " + property);
                        if (captureError.getCode() == CaptureError.ESKT_FAVORITENOTEMPTY) {
                            enableManualDiscoveryButton(true);
                        } else if (captureError.getCode() == CaptureError.ESKT_LOCATIONPERMISSIONMISSING) {
                            try {
                                Log.d(TAG, "start activity");

                                Intent intent = new Intent();
                                intent.putExtra(EXTRA_ERROR_CODE, CaptureError.ESKT_LOCATIONPERMISSIONMISSING);
                                intent.setClassName("com.socketmobile.companion.staging", "com.socketmobile.companion.GetPermission");
                                startActivityForResult(intent, REQUEST_CODE_LOCATION_PERMISSION_MISSING);
                                return;
                            } catch (ActivityNotFoundException activityNotFoundException) {
                                // TODO : write your code
                                activityNotFoundException.printStackTrace();
                            }
                        }
                        //Capture error handling
                        if (captureError.getCode() == CaptureError.ESKT_BLUETOOTHPERMISSIONMISSING) {
                            Intent intent = new Intent();
                            intent.putExtra(EXTRA_ERROR_CODE, CaptureError.ESKT_BLUETOOTHPERMISSIONMISSING);
                            intent.setClassName("com.socketmobile.companion", "com.socketmobile.companion.GetPermission");
                            permissionActivityResultLauncher.launch(intent);
                        }

                        String err = String.format(getResources().getString(R.string.set_property_error), "" + captureError.getCode());
                        Toast.makeText(getApplicationContext(), err, Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
    };

    PropertyCallback triggerPropertyCallback = new PropertyCallback() {
        @Override
        public void onComplete(CaptureError captureError, Property property) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if(captureError != null) {
                        Toast.makeText(MainActivity.this, "Set Trigger Failed : " + "Code : " + captureError.getCode() + " : " + captureError.getMessage(), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(MainActivity.this, "Set Trigger Completed", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
    };

    PropertyCallback themePropertyCallback = new PropertyCallback() {
        @Override
        public void onComplete(CaptureError captureError, Property property) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if(captureError != null) {
                        Toast.makeText(MainActivity.this, "Set Theme Failed : " + "Code : " + captureError.getCode() + " : " + captureError.getMessage(), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(MainActivity.this, "Set Theme Completed", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
    };

    ActivityResultLauncher<Intent> permissionActivityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Intent data = result.getData();
                        int permissionResult = data.getIntExtra(Permissions.EXTRA_GRANT_RESULT, PackageManager.PERMISSION_DENIED);
                        switch (permissionResult) {
                            case PackageManager.PERMISSION_GRANTED:
                                // Continue using the Capture SDK to scan barcodes
                                break;
                            case PackageManager.PERMISSION_DENIED:
                                // Notify user that permission is required to use bluetooth scanner
                                break;
                        }

                    }
                }
            });

    /** Manual discovery looks for Bluetooth LE devices available in the vicinity.
     * Manual discovery runs for the specified duration and sends a DeviceDiscoveryEvent each time a Socket Mobile Bluetooth LE device is discovered.
     * */
    public void startManualDiscovery(View view) {

        enableManualDiscoveryButton(false);
        setFavoriteSpinner();
        mCaptureClient.addDevice((byte) BLUETOOTH_LOW_ENERGY, new PropertyCallback() {
            @Override
            public void onComplete(@Nullable CaptureError captureError, @Nullable Property property) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if(captureError != null) {
                            Toast.makeText(MainActivity.this, "addDevice Failed : " + "Code : " + captureError.getCode() + " : " + captureError.getMessage(), Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(MainActivity.this, "addDevice Completed", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        });
    }

    /** Example to programmatically trigger a scan using an on-screen button
     **/
    public void onDisconnectButton(View v) {

        // Navigate up the view hierarchy: Button -> LinearLayout -> HorizontalScrollView -> ConstraintLayout (row) -> ListView
        View parentRow = (View) v.getParent().getParent().getParent();
        ListView listView = (ListView) parentRow.getParent();
        final int position = listView.getPositionForView(parentRow);

        DeviceClient deviceClient = mDeviceClientList.get(position);

        mCaptureClient.disconnectDevice(deviceClient.getUniqueId(), new PropertyCallback() {
            @Override
            public void onComplete(@Nullable CaptureError captureError, @Nullable Property property) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if(captureError != null) {
                            Toast.makeText(MainActivity.this, "disconnectDevice Failed : " + "Code : " + captureError.getCode() + " : " + captureError.getMessage(), Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(MainActivity.this, "disconnectDevice Completed", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        });
    }

    public void onRemoveButton(View v) {
        // Navigate up the view hierarchy: Button -> LinearLayout -> HorizontalScrollView -> ConstraintLayout (row) -> ListView
        View parentRow = (View) v.getParent().getParent().getParent();
        ListView listView = (ListView) parentRow.getParent();
        final int position = listView.getPositionForView(parentRow);

        DeviceClient deviceClient = mDeviceClientList.get(position);

        mCaptureClient.removeDevice(deviceClient.getUniqueId(), new PropertyCallback() {
            @Override
            public void onComplete(@Nullable CaptureError captureError, @Nullable Property property) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if(captureError != null) {
                            Toast.makeText(MainActivity.this, "removeDevice Failed : " + "Code : " + captureError.getCode() + " : " + captureError.getMessage(), Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(MainActivity.this, "removeDevice Completed", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        });
    }

    /** Example to programmatically trigger a scan using an on-screen button
     **/
    public void onTriggerButton(View v) {

        // Navigate up the view hierarchy: Button -> LinearLayout -> HorizontalScrollView -> ConstraintLayout (row) -> ListView
        View parentRow = (View) v.getParent().getParent().getParent();
        ListView listView = (ListView) parentRow.getParent();
        final int position = listView.getPositionForView(parentRow);

        DeviceClient deviceClient = mDeviceClientList.get(position);

        PopupMenu popupMenu = new PopupMenu(MainActivity.this, v);
        popupMenu.getMenuInflater().inflate(R.menu.trigger_menu, popupMenu.getMenu());
        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                Property property = null;
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
            }
        });
        popupMenu.show();
    }

    /** Example to change Themes Selection.
     * This allows to choose LEDs sequences that are played on S550 or S370 devices when scanning
     **/
    public void onThemeButton(View v) {
        // Navigate up the view hierarchy: Button -> LinearLayout -> HorizontalScrollView -> ConstraintLayout (row) -> ListView
        View parentRow = (View) v.getParent().getParent().getParent();
        ListView listView = (ListView) parentRow.getParent();
        final int position = listView.getPositionForView(parentRow);

        DeviceClient deviceClient = mDeviceClientList.get(position);
        PopupMenu popupMenu = new PopupMenu(MainActivity.this, v);

        popupMenu.getMenuInflater().inflate(R.menu.theme_menu, popupMenu.getMenu());
        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
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
            }
        });
        popupMenu.show();
    }
}
