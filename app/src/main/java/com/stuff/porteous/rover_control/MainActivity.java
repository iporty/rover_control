package com.stuff.porteous.rover_control;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;


// Async task to grab the results every x seconds
// HandlerThread to process requests to send UDP
public class MainActivity extends ActionBarActivity {

    private SeekBar throttle_bar_;
    private SeekBar steering_bar_;
    private TextView settings_text_;
    private int count_;
    private MessageHandler msg_handler_;
    private MyTaskThread task_thread_;
    private Button connect_button_;
    private SharedPreferences settings_;
    private UDPConnection udp_connection_;
    private MjpegView mjpeg_view_;
    private InputStream input_stream_;
    private MjpegInputStream mjpeg_input_stream_;

    static final private int CONNECT = 0;
    static final private int SEND_SETTING = 1;
    static final private int DISCONNECT = 2;
    private Button disconnect_button_;

    class UDPConnection {
        public InetAddress inet_address_;
        public DatagramSocket socket_;
        public boolean connected_ = false;

        public boolean isConnected() {
            return connected_;
        }

        public DatagramSocket GetSocket() {
            if (connected_) {
                return socket_;
            } else {
                return null;
            }
        }

        public void DisconnectSocket() {
            if (socket_.isConnected()) {
                socket_.disconnect();
            }
        }

        public void Connect(String address, int port) {
            if (socket_ != null && socket_.isConnected()) {
                connected_ = true;
                return;
            } else {
                connected_ = false;
            }

            try {
                inet_address_ = InetAddress.getByName(address);
                socket_ = new DatagramSocket();
                InetSocketAddress socket_address = new InetSocketAddress(inet_address_, port);
                socket_.connect(socket_address);
                connected_ = true;
            } catch (Exception e) {
                connected_ = false;
                Log.e("UDPConnections", e.toString());
            }
        }

        public void Disconnect() {
            if (socket_ != null && socket_.isConnected()) {
                socket_.disconnect();
            }
        }
    }


    private void initializeUIVariables() {
        throttle_bar_ = (SeekBar) findViewById(R.id.throttle);
        steering_bar_ = (SeekBar) findViewById(R.id.steering);
        settings_text_ = (TextView) findViewById(R.id.settingsView);
        mjpeg_view_ = (MjpegView) findViewById(R.id.mjpeg_view);

        connect_button_ = (Button) findViewById(R.id.connect);
        connect_button_.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Message msg = Message.obtain(msg_handler_, CONNECT, 0, 0);
                msg_handler_.sendMessage(msg);
            }
        });

        disconnect_button_ = (Button) findViewById(R.id.disconnect);
        disconnect_button_.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Message msg = Message.obtain(msg_handler_, DISCONNECT, 0, 0);
                msg_handler_.sendMessage(msg);
            }
        });
    }

    class MessageHandler extends Handler {

        private void ConnectOp() {
            String address = settings_.getString(SettingsActivity.rover_address_key, "");

            mjpeg_input_stream_ = MjpegInputStream.read(address);
            mjpeg_view_.setSource(mjpeg_input_stream_);

            int port = settings_.getInt(SettingsActivity.rover_port_key, 0);
            udp_connection_.Connect(address, port);

            String connected = "not connected";
            if (udp_connection_.isConnected()) connected = "connected";

            Context  context = getApplicationContext();
            int duration = Toast.LENGTH_SHORT;
            Toast toast = Toast.makeText(context, connected, duration);
            toast.show();
        }

        private void DisconnectOp() {
            udp_connection_.Disconnect();
            udp_connection_.connected_ = false;

            mjpeg_view_.startPlayback();
        }

        @Override
        public void handleMessage(Message msg) {
            count_++;
            switch (msg.what) {
                case CONNECT:
                    ConnectOp();
                    break;
                case DISCONNECT:
                    DisconnectOp();
                    break;
                case SEND_SETTING:
                    if (udp_connection_.isConnected()) {

                        ByteBuffer bb = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
                        bb.putInt(msg.arg1);
                        bb.putInt(msg.arg2);
                        DatagramPacket datagram = new DatagramPacket(bb.array(), 8);

                        try {
                            udp_connection_.socket_.send(datagram);
                        } catch (Exception e) {
                            Log.e("HandleMessage", e.toString());
                        }
                    }
                    break;
                default:
                    break;

            }
            //Log.e("MessageHandler", "arg1 " + msg.arg1);
        }
    }

    class MyTaskThread extends Thread {
        @Override
        public void run() {
            try {
                Looper.prepare();

                msg_handler_ = new MessageHandler();

                Looper.loop();

            } catch (Throwable T) {
                Log.e("MyTaskHandler", T.toString());
            }

        }
    }


    private void initializeTaskHandler() {

        Log.d("initializeTaskHandler","start");

        // Start the message handler thread
        task_thread_ = new MyTaskThread();
        task_thread_.start();


        Log.e("initializeTaskHandler", "started");

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {

        }
    }

    public void ReadControlsAndUpdate() {
        // Post task to the task handler
        int throttle_setting = throttle_bar_.getProgress();
        int steering_setting = steering_bar_.getProgress();

        // Delete all messages currently in the queue so we don't end up with a
        // backlog of delayed commands
        msg_handler_.removeMessages(SEND_SETTING);

        Message msg1 = Message.obtain(msg_handler_, SEND_SETTING, 1, throttle_setting);
        Message msg2 = Message.obtain(msg_handler_, SEND_SETTING, 2, steering_setting);

        msg_handler_.sendMessage(msg1);
        msg_handler_.sendMessage(msg2);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                settings_text_.setText("T: " + throttle_bar_.getProgress() + " S: " +
                        steering_bar_.getProgress() + " count: " + count_);
            }
        });
    }

    class SeekBarUpdate implements SeekBar.OnSeekBarChangeListener {

        @Override
        public void onProgressChanged(SeekBar seek_bar, int progressValue, boolean fromUser) {
            ReadControlsAndUpdate();
        }

        public void onStartTrackingTouch(SeekBar seek_bar) {
        }

        public void onStopTrackingTouch(SeekBar seek_bar) {
        }
    }


    // Handles the job of scanning the UI every X seconds.
    private class UpdateLooper extends AsyncTask<Void, Void, Void>  {
        final int UpdateInterval = 500;

        @Override
        protected Void doInBackground(Void... params) {
            ++count_;
            try {
                Thread.sleep(UpdateInterval); // milliseconds
            } catch (InterruptedException e)  {
                Log.e("Update Looper Sleep", e.toString());
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            ReadControlsAndUpdate();
            new UpdateLooper().execute();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeUIVariables();
        settings_ = getApplicationContext().getSharedPreferences("prefs", MODE_PRIVATE);
        udp_connection_ = new UDPConnection();

        settings_text_.setText("T: " + throttle_bar_.getProgress() + " S: " +
                                steering_bar_.getProgress());

        throttle_bar_.setProgress(50);
        steering_bar_.setProgress(50);

        throttle_bar_.setOnSeekBarChangeListener(new SeekBarUpdate());
        steering_bar_.setOnSeekBarChangeListener(new SeekBarUpdate());

        initializeTaskHandler();

        new UpdateLooper().execute();


    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        Log.e("MainActivity:options", item.toString());
        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            Intent myIntent = new Intent(this, SettingsActivity.class);
            startActivityForResult(myIntent, 0);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
