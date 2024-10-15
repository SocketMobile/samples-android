package com.example.socketmobile.android.hellocaptureble;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

public class NonCaptureActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_non_capture);
    }

    public void prevActivity(View view) {
        finish();
    }

    public void nextActivity(View view) {
        startActivity(new Intent(this, SecondCaptureActivity.class));
    }
}
