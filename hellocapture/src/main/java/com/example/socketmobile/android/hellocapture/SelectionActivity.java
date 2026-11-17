package com.example.socketmobile.android.hellocapture;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.socketmobile.android.hellocapture.databinding.ActivitySelectionBinding;

public class SelectionActivity extends AppCompatActivity {

    private ActivitySelectionBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySelectionBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setTitle(getString(R.string.app_name) + " " + BuildConfig.VERSION_NAME);

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        binding.defaultViewFinder.setOnClickListener(view -> {
            Intent intent = new Intent(SelectionActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        });

        binding.customViewFinder.setOnClickListener(view -> {
            startActivity(new Intent(SelectionActivity.this, CustomViewActivity.class));
            finish();
        });
    }
}
