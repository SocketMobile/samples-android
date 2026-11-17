package com.example.socketmobile.android.hellocapture;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;

import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.socketmobile.android.hellocapture.databinding.ActivityCustomViewBinding;
import com.socketmobile.capture.CaptureError;
import com.socketmobile.capture.SocketCamStatus;
import com.socketmobile.capture.android.events.ConnectionStateEvent;
import com.socketmobile.capture.client.CaptureClient;
import com.socketmobile.capture.client.ConnectionState;
import com.socketmobile.capture.client.DataEvent;
import com.socketmobile.capture.client.DeviceClient;
import com.socketmobile.capture.client.DeviceStateEvent;
import com.socketmobile.capture.socketcam.client.CaptureExtension;
import com.socketmobile.capture.socketcam.view.SocketCamFragment;
import com.socketmobile.capture.troy.ExtensionScope;
import com.socketmobile.capture.types.DeviceType;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class CustomViewActivity extends AppCompatActivity {
    private static final String TAG = CustomViewActivity.class.getSimpleName();
    private static final int PERMISSION_REQUEST_CAMERA = 0x10;

    private ActivityCustomViewBinding binding;
    private CaptureClient mCaptureClient;
    private DeviceClient mDevice;

    private CaptureExtension mCaptureExtension;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCustomViewBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        setTitle(getString(R.string.app_name) + " " + BuildConfig.VERSION_NAME);

        binding.triggerButton.setEnabled(false);
        binding.triggerButton.setOnClickListener(view -> {
            if (ContextCompat.checkSelfPermission(getBaseContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(CustomViewActivity.this, new String[]{Manifest.permission.CAMERA}, PERMISSION_REQUEST_CAMERA);
            } else {
                showViewFinder();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCaptureExtension != null)
            mCaptureExtension.stop();
    }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    public void onCaptureServiceConnectionStateChange(ConnectionStateEvent event) {
        ConnectionState state = event.getState();
        mCaptureClient = event.getClient();
        switch (state.intValue()) {
            case ConnectionState.READY:
            // This sticky event replays on every registration (e.g. after a rotation):
            // only build the extension once
            if (mCaptureExtension != null) {
                break;
            }
            mCaptureClient.setSocketCamStatus(SocketCamStatus.ENABLE, (captureError, property) -> {
                mCaptureExtension = new CaptureExtension.Builder()
                        .setContext(CustomViewActivity.this)
                        .setClientHandle(mCaptureClient.getHandle())
                        .setExtensionScope(ExtensionScope.GLOBAL)
                        .setListener(mListener)
                        .setCustomViewListener(deviceHandle -> runOnUiThread(() ->
                                getSupportFragmentManager().beginTransaction()
                                        .add(R.id.socketcam_fragment, SocketCamFragment.newInstance(deviceHandle, mDevice, true))
                                        .commit()))
                        .build();
                mCaptureExtension.start();
            });
            break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    public void onCaptureDeviceStateChange(DeviceStateEvent event) {

        Log.d(TAG, "CustomViewActivity : onService() called with: event = [" + event.getState().intValue() + "]");
        Log.d(TAG, "CustomViewActivity : onService() called with: type = [" + event.getDevice().getDeviceType() + "]");
        if (DeviceType.isCameraScanner(event.getDevice().getDeviceType())) {
            binding.triggerButton.setEnabled(true);
            binding.deviceStatus.setText(R.string.status_ready);
            binding.deviceStatus.setTextColor(ContextCompat.getColor(this, R.color.statusColorReady));

            mDevice = event.getDevice();
        }
    }



    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onScan(DataEvent event) {
        if (event.getData() != null) {
            binding.decodedData.setText(event.getData().getString());
        }
    }

    public void showViewFinder() {
        if(mDevice == null) return;

        mDevice.triggerContinuous((captureError, property) -> runOnUiThread(() -> {
            if(captureError != null) {
                Toast.makeText(getApplicationContext(),
                        getString(R.string.trigger_failed, captureError.getMessage()),
                        Toast.LENGTH_SHORT).show();
            }
        }));
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
                Toast.makeText(this, R.string.camera_permission_needed, Toast.LENGTH_SHORT).show();
            }
        }
    }
}
