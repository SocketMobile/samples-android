package com.example.socketmobile.android.hellocapture;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.socketmobile.capture.CaptureError;
import com.socketmobile.capture.Property;
import com.socketmobile.capture.SocketCamStatus;
import com.socketmobile.capture.android.Capture;
import com.socketmobile.capture.android.events.ConnectionStateEvent;
import com.socketmobile.capture.client.CaptureClient;
import com.socketmobile.capture.client.ConnectionState;
import com.socketmobile.capture.client.DataEvent;
import com.socketmobile.capture.client.DeviceClient;
import com.socketmobile.capture.client.DeviceStateEvent;
import com.socketmobile.capture.client.callbacks.PropertyCallback;
import com.socketmobile.capture.socketcam.client.CaptureExtension;
import com.socketmobile.capture.socketcam.view.SocketCamFragment;
import com.socketmobile.capture.troy.ExtensionScope;
import com.socketmobile.capture.types.DeviceType;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class CustomViewActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CAMERA = 0x10;
    private CaptureClient mCaptureClient;
    private DeviceClient mDevice;
    private TextView mDecodedDataTextView;
    private Button mTriggerButton;

    private CaptureExtension mCaptureExtension;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_custom_view);

        Capture.builder(getApplicationContext())
                .enableLogging(BuildConfig.DEBUG)
                .build();

        setTitle(getResources().getString(R.string.app_name) + " " + BuildConfig.VERSION_NAME);

        mDecodedDataTextView = findViewById(R.id.decoded_data);
        mTriggerButton = findViewById(R.id.trigger_button);
        mTriggerButton.setEnabled(false);
        mTriggerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP &&
                        ContextCompat.checkSelfPermission(getBaseContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(CustomViewActivity.this, new String[]{Manifest.permission.CAMERA},PERMISSION_REQUEST_CAMERA);
                } else {
                    showViewFinder();
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCaptureExtension != null)
            mCaptureExtension.stop(); // TODO : Chitra : call close client once the device is closed?
    }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    public void onCaptureServiceConnectionStateChange(ConnectionStateEvent event) {
        ConnectionState state = event.getState();
        mCaptureClient = event.getClient();
        switch (state.intValue()) {
            case ConnectionState.READY:
            mCaptureClient.setSocketCamStatus(SocketCamStatus.ENABLE, new PropertyCallback() {
                @Override
                public void onComplete(CaptureError captureError, Property property) {


                    if(mCaptureClient != null) {
                        mCaptureClient.setSocketCamStatus(SocketCamStatus.ENABLE, new PropertyCallback() {
                            @Override
                            public void onComplete(CaptureError captureError, Property property) {
                                mCaptureExtension = new CaptureExtension.Builder()
                                        .setContext(CustomViewActivity.this)
                                        .setClientHandle(mCaptureClient.getHandle())
                                        .setExtensionScope(ExtensionScope.GLOBAL)
                                        .setListener(mListener)
                                        .setCustomViewListener(new CaptureExtension.CustomViewListener() {
                                            @Override
                                            public void onViewReady(int deviceHandle) {
                                                runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        getSupportFragmentManager().beginTransaction()
                                                                .add(R.id.socketcam_fragment, SocketCamFragment.newInstance(deviceHandle, mDevice, true))
                                                                .commit();
                                                    }
                                                });
                                            }
                                        })
                                        .build();
                                mCaptureExtension.start();
                            }
                        });
                    }
                }
            });
            break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    public void onCaptureDeviceStateChange(DeviceStateEvent event) {
        //if(event.getDevice().getDeviceType() == kModelSocketCamC820) {
        if (DeviceType.isCameraScanner(event.getDevice().getDeviceType())) {
            mTriggerButton.setEnabled(true);
            TextView deviceStatusTextView = findViewById(R.id.device_status);
            deviceStatusTextView.setText("Ready");
            deviceStatusTextView.setTextColor(Color.parseColor("#2AA324"));

            mDevice = event.getDevice();
        }
    }



    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    public void onScan(DataEvent event) {
        runOnUiThread(new Runnable() {
            public void run() {
                if(event.getData() != null) {
                    mDecodedDataTextView.setText(event.getData().getString());
                }
            }
        });
    }

    public void showViewFinder() {
        if(mDevice == null) return;

        mDevice.triggerContinuous(new PropertyCallback() {
            @Override
            public void onComplete(CaptureError captureError, Property property) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        if(captureError != null) {
                            Toast.makeText(getApplicationContext(), "Trigger failed with error : " + captureError.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        });
    }


    CaptureExtension.Listener mListener = new CaptureExtension.Listener() {
        @Override
        public void onExtensionStateChanged(ConnectionState connectionState) {
            // TODO : implementation to update UI
        }

        @Override
        public void onError(CaptureError error) {
            // TODO : implementation to update UI
        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showViewFinder();
            } else {
                Toast.makeText(this, "Please allow camera permission to scan", Toast.LENGTH_SHORT).show();
            }
        }
    }
}