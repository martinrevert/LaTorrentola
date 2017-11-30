package com.martinrevert.latorrentola;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;


public class OpcionesActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getFragmentManager().beginTransaction()
                .replace(R.id.preferencecontent, new SettingsFragment())
                .commit();
    }
}
