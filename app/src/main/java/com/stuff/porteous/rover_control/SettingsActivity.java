package com.stuff.porteous.rover_control;

import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.TextView;


public class SettingsActivity extends ActionBarActivity {
    private SharedPreferences settings_;
    private String rover_address_;
    private int rover_port_;
    private EditText address_text_;
    private EditText port_text_;

    public static final String rover_address_key = "rover_address";
    public static final String rover_port_key = "rover_port";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Initialize Settings from preferences
        settings_ = getApplicationContext().getSharedPreferences("prefs", MODE_PRIVATE);

        rover_address_ = settings_.getString(rover_address_key, "127.0.0.1");
        address_text_ = (EditText) findViewById(R.id.roverAddressText);
        address_text_.setText(rover_address_);

        rover_port_ = settings_.getInt(rover_port_key, 8888);
        port_text_ = (EditText) findViewById(R.id.portText);
        port_text_.setText(Integer.toString(rover_port_));
    }

    @Override
    protected  void onPause() {
        SharedPreferences.Editor editor = settings_.edit();
        editor.putString(rover_address_key, address_text_.getText().toString());
        editor.putInt(rover_port_key, rover_port_);
        editor.commit();
        super.onPause();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_settings, menu);


        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();


        Log.e("SettingsActivity:options", item.toString());
        //noinspection SimplifiableIfStatement
        if (id == R.id.rover_control) {
            Intent myIntent = new Intent(this, MainActivity.class);
            startActivityForResult(myIntent, 0);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
