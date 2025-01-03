package com.example.indoorlocator;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class MainScreenActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_screen);

        Button btnDataCollection = findViewById(R.id.btn_data_collection);
        btnDataCollection.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainScreenActivity.this, WifiCollectActivity.class);
                startActivity(intent);
            }
        });

        Button btnStartLocate = findViewById(R.id.btn_start_locate);
        btnStartLocate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainScreenActivity.this, LocateActivity.class);
                startActivity(intent);
            }
        });
    }
}

