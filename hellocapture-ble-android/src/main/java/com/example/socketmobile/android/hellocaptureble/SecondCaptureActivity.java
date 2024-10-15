package com.example.socketmobile.android.hellocaptureble;

import android.os.Bundle;
//import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.socketmobile.capture.CaptureError;
import com.socketmobile.capture.Property;
import com.socketmobile.capture.android.events.ConnectionStateEvent;
import com.socketmobile.capture.client.CaptureClient;
import com.socketmobile.capture.client.DataEvent;
import com.socketmobile.capture.client.callbacks.PropertyCallback;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class SecondCaptureActivity extends AppCompatActivity {

    private CaptureClient mCaptureClient;
    private Button mGetPropertyBtn;

    private static final String TAG = SecondCaptureActivity.class.getName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_second_capture);
        mGetPropertyBtn = findViewById(R.id.get_property);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onData(DataEvent event) {
        TextView tv = findViewById(R.id.hello_scan);
        if (tv != null) {
            tv.append("\n" + event.getData().getString());
        }
    }

    public void prevActivity(View view) {
        finish();
    }

    public void getProperty(View view) {
        Toast.makeText(getApplicationContext(), "test", Toast.LENGTH_SHORT).show();

        mCaptureClient.getCaptureVersion(propCallback);
    }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    public void onCaptureServiceConnectionStateChange(ConnectionStateEvent event) {
        Log.d(TAG, "onService() called with: event = [" + event + "]");
        mCaptureClient = event.getClient();
    }

    PropertyCallback propCallback = new PropertyCallback(){

        @Override
        public void onComplete(final CaptureError error, final Property property) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {

                    if(property != null) {
                        Toast.makeText(getApplicationContext(), property.getString(), Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
    };
}
