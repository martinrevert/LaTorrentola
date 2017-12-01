package com.martinrevert.latorrentola;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;

import com.crashlytics.android.Crashlytics;

import io.fabric.sdk.android.Fabric;


public class OpcionesActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_opciones);
        Fabric.with(this, new Crashlytics());
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("Settings");
        setSupportActionBar(toolbar);

        if (getFragmentManager().findFragmentById(R.id.preferencecontent) == null) {
            getFragmentManager().beginTransaction()
                    .replace(R.id.preferencecontent, new SettingsFragment())
                    .commit();
        }
    }
}
