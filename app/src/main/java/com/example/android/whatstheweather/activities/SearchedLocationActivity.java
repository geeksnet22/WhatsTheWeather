package com.example.android.whatstheweather.activities;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.widget.Toolbar;

import androidx.appcompat.app.AppCompatActivity;

import com.example.android.whatstheweather.R;
import com.example.android.whatstheweather.utils.CommonUtilFunctions;

import org.json.JSONException;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

public class SearchedLocationActivity extends AppCompatActivity {

    private Toolbar toolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location_searched);

        toolbar = findViewById(R.id.toolbar);

        String location = getIntent().getStringExtra("location");
        String rawData = getIntent().getStringExtra("rawData");
        try {
            CommonUtilFunctions.setupRefreshListener(toolbar, location,this, this);
            CommonUtilFunctions.fetchDataAndSetupLayout(toolbar, rawData, location,false,
                    this, this);
        }
        catch (JSONException | InterruptedException | ExecutionException | IOException e) {
            e.printStackTrace();
            setSupportActionBar(toolbar);
            findViewById(R.id.mainScroll).setVisibility(View.INVISIBLE);
        }
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
}

