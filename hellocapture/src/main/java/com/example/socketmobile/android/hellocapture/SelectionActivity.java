package com.example.socketmobile.android.hellocapture;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.socketmobile.capture.android.Capture;

public class SelectionActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_selection);

        setTitle(getResources().getString(R.string.app_name) + " " + BuildConfig.VERSION_NAME);

        Capture.builder(getApplicationContext())
                .enableLogging(BuildConfig.DEBUG)
                .build();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        Button defaultButton = findViewById(R.id.default_view_finder);
        defaultButton.setOnClickListener(view -> {
            Intent intent = new Intent(SelectionActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        });

        Button customButton = findViewById(R.id.custom_view_finder);
        customButton.setOnClickListener(view -> {
            Intent intent = new Intent(SelectionActivity.this, CustomViewActivity.class);
            startActivity(intent);
            finish();
        });
    }
}