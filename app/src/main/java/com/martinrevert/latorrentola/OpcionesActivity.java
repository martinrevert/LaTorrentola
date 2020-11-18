package com.martinrevert.latorrentola;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import androidx.appcompat.widget.Toolbar;




public class OpcionesActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_opciones);
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
