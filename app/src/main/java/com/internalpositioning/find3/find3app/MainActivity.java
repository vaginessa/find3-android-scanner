package com.internalpositioning.find3.find3app;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.util.Linkify;
import android.util.Log;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.ToggleButton;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONObject;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    // logging
    private final String TAG = "MainActivity";

    // background manager
    private PendingIntent recurringLl24 = null;
    private Intent ll24 = null;
    AlarmManager alarms = null;
    WebSocketClient mWebSocketClient = null;
    Timer timer = null;
    private RemindTask oneSecondTimer = null;

    private int receivedMessages = 0;

    @Override
    protected void onDestroy() {
        Log.d(TAG, "MainActivity onDestroy()");
        if (alarms != null) alarms.cancel(recurringLl24);
        if (timer != null) timer.cancel();
        if (mWebSocketClient != null) {
            mWebSocketClient.close();
        }
        android.app.NotificationManager mNotificationManager = (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        assert mNotificationManager != null;
        mNotificationManager.cancel(0);
        destroyScanService();
        super.onDestroy();
    }

    private void destroyScanService() {
        Intent scanService = new Intent(this, ScanService.class);
        stopService(scanService);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // check permissions
        int permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WAKE_LOCK, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.INTERNET, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.CHANGE_WIFI_STATE, Manifest.permission.ACCESS_WIFI_STATE}, 1);
        }

        TextView rssi_msg = findViewById(R.id.textOutput);
        rssi_msg.setText(R.string.not_running);

        // check to see if there are preferences
        SharedPreferences sharedPref = MainActivity.this.getPreferences(Context.MODE_PRIVATE);
        EditText familyNameEdit = findViewById(R.id.familyName);
        familyNameEdit.setText(sharedPref.getString("familyName", ""));
        EditText deviceNameEdit = findViewById(R.id.deviceName);
        deviceNameEdit.setText(sharedPref.getString("deviceName", ""));
        EditText serverAddressEdit = findViewById(R.id.serverAddress);
        serverAddressEdit.setText(sharedPref.getString("serverAddress", ((EditText) findViewById(R.id.serverAddress)).getText().toString()));
        CheckBox checkBoxAllowGPS = findViewById(R.id.allowGPS);
        checkBoxAllowGPS.setChecked(sharedPref.getBoolean("allowGPS", false));

        ToggleButton toggleButtonTracking = findViewById(R.id.toggleScanType);
        toggleButtonTracking.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                cancel();

                CompoundButton scanButton = findViewById(R.id.toggleButton);
                scanButton.setChecked(false);
            }
        });

        ToggleButton toggleButton = findViewById(R.id.toggleButton);
        toggleButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    TextView rssi_msg = findViewById(R.id.textOutput);
                    String familyName = ((EditText) findViewById(R.id.familyName)).getText().toString().toLowerCase();
                    if (familyName.equals("")) {
                        rssi_msg.setText(R.string.no_family);
                        buttonView.toggle();
                        return;
                    }

                    String serverAddress = ((EditText) findViewById(R.id.serverAddress)).getText().toString().toLowerCase();
                    if (serverAddress.equals("")) {
                        rssi_msg.setText(R.string.no_server);
                        buttonView.toggle();
                        return;
                    }
                    if (!serverAddress.contains("http")) {
                        rssi_msg.setText(R.string.no_valid_server);
                        buttonView.toggle();
                        return;
                    }
                    String deviceName = ((EditText) findViewById(R.id.deviceName)).getText().toString().toLowerCase();
                    if (deviceName.equals("")) {
                        rssi_msg.setText(R.string.no_device);
                        buttonView.toggle();
                        return;
                    }
                    boolean allowGPS = ((CheckBox) findViewById(R.id.allowGPS)).isChecked();
                    Log.d(TAG, "allowGPS is checked: " + allowGPS);
                    String locationName = ((EditText) findViewById(R.id.locationName)).getText().toString().toLowerCase();

                    CompoundButton trackingButton = findViewById(R.id.toggleScanType);
                    if (!trackingButton.isChecked()) {
                        locationName = "";
                    } else {
                        if (locationName.equals("")) {
                            rssi_msg.setText(R.string.no_location);
                            buttonView.toggle();
                            return;
                        }
                    }

                    SharedPreferences sharedPref = MainActivity.this.getPreferences(Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = sharedPref.edit();
                    editor.putString("familyName", familyName);
                    editor.putString("deviceName", deviceName);
                    editor.putString("serverAddress", serverAddress);
                    editor.putString("locationName", locationName);
                    editor.putBoolean("allowGPS", allowGPS);
                    editor.apply();

                    rssi_msg.setText(R.string.running);
                    // 24/7 alarm
                    ll24 = new Intent(MainActivity.this, AlarmReceiverLife.class);
                    Log.d(TAG, "setting familyName to [" + familyName + "]");
                    ll24.putExtra("familyName", familyName);
                    ll24.putExtra("deviceName", deviceName);
                    ll24.putExtra("serverAddress", serverAddress);
                    ll24.putExtra("locationName", locationName);
                    ll24.putExtra("allowGPS", allowGPS);
                    recurringLl24 = PendingIntent.getBroadcast(MainActivity.this, 0, ll24, PendingIntent.FLAG_CANCEL_CURRENT);
                    alarms = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
                    assert alarms != null;
                    alarms.set(AlarmManager.RTC_WAKEUP, SystemClock.currentThreadTimeMillis(), recurringLl24);
                    timer = new Timer();
                    oneSecondTimer = new RemindTask();
                    timer.scheduleAtFixedRate(oneSecondTimer, 1000, 1000);
                    connectWebSocket();

                    String scanningMessage = "Scanning for " + familyName + "/" + deviceName;
                    if (!locationName.equals("")) {
                        scanningMessage += " at " + locationName;
                    }
                    NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(MainActivity.this, "FIND3")
                            .setSmallIcon(R.drawable.ic_stat_name)
                            .setContentTitle(scanningMessage)
                            .setContentIntent(recurringLl24);
                    //specifying an action and its category to be triggered once clicked on the notification
                    Intent resultIntent = new Intent(MainActivity.this, MainActivity.class);
                    resultIntent.setAction("android.intent.action.MAIN");
                    resultIntent.addCategory("android.intent.category.LAUNCHER");
                    PendingIntent resultPendingIntent = PendingIntent.getActivity(MainActivity.this, 0, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);
                    notificationBuilder.setContentIntent(resultPendingIntent);

                    android.app.NotificationManager notificationManager =
                            (android.app.NotificationManager) MainActivity.this.getSystemService(Context.NOTIFICATION_SERVICE);
                    assert notificationManager != null;
                    notificationManager.notify(0 /* ID of notification */, notificationBuilder.build());

                    final TextView myClickableUrl = findViewById(R.id.textInstructions);
                    myClickableUrl.setText(String.format(Locale.getDefault(), "See your results in realtime: %s/view/location/%s/%s", serverAddress, familyName, deviceName));
                    Linkify.addLinks(myClickableUrl, Linkify.WEB_URLS);
                } else {
                    cancel();
                }
            }
        });
    }

    private void cancel() {
        TextView rssi_msg = findViewById(R.id.textOutput);
        rssi_msg.setText(R.string.not_running);
        Log.d(TAG, "toggle set to false");
        if (alarms != null) alarms.cancel(recurringLl24);
        android.app.NotificationManager mNotificationManager = (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        assert mNotificationManager != null;
        mNotificationManager.cancel(0);
        if (timer != null) timer.cancel();
        destroyScanService();
        receivedMessages = 0;
    }

    private void connectWebSocket() {
        URI uri;
        try {
            String serverAddress = ((EditText) findViewById(R.id.serverAddress)).getText().toString();
            String familyName = ((EditText) findViewById(R.id.familyName)).getText().toString();
            String deviceName = ((EditText) findViewById(R.id.deviceName)).getText().toString();
            serverAddress = serverAddress.replace("http", "ws");
            uri = new URI(serverAddress + "/ws?family=" + familyName + "&device=" + deviceName);
            Log.d("WebSocket", "connect to WebSocket at " + uri.toString());
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return;
        }

        mWebSocketClient = new WebSocketClient(uri) {
            @Override
            public void onOpen(ServerHandshake serverHandshake) {
                Log.i("WebSocket", "Opened");
                mWebSocketClient.send("Hello");
            }

            @Override
            public void onMessage(String s) {
                final String message = s;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Log.d("WebSocket", "message: " + message);
                        JSONObject json;
                        JSONObject fingerprint = null;
                        JSONObject sensors = null;
                        JSONObject bluetooth = null;
                        JSONObject wifi = null;
                        String deviceName = "";
                        String locationName = "";
                        String familyName = "";
                        try {
                            json = new JSONObject(message);
                        } catch (Exception e) {
                            Log.d("WebSocket", "json error: " + e.toString());
                            return;
                        }
                        try {
                            fingerprint = new JSONObject(json.get("sensors").toString());
                            Log.d("WebSocket", "fingerprint: " + fingerprint);
                        } catch (Exception e) {
                            Log.d("WebSocket", "json error: " + e.toString());
                        }
                        try {
                            assert fingerprint != null;
                            sensors = new JSONObject(fingerprint.get("s").toString());
                            deviceName = fingerprint.get("d").toString();
                            familyName = fingerprint.get("f").toString();
                            locationName = fingerprint.get("l").toString();
                            Log.d("WebSocket", "sensors: " + sensors);
                        } catch (Exception e) {
                            Log.d("WebSocket", "json error: " + e.toString());
                        }
                        try {
                            assert sensors != null;
                            wifi = new JSONObject(sensors.get("wifi").toString());
                            Log.d("WebSocket", "wifi: " + wifi);
                        } catch (Exception e) {
                            Log.d("WebSocket", "json error: " + e.toString());
                        }
                        try {
                            bluetooth = new JSONObject(sensors.get("bluetooth").toString());
                            Log.d("WebSocket", "bluetooth: " + bluetooth);
                        } catch (Exception e) {
                            Log.d("WebSocket", "json error: " + e.toString());
                        }
                        assert bluetooth != null;
                        Log.d("WebSocket", bluetooth.toString());
                        int bluetoothPoints = bluetooth.length();
                        assert wifi != null;
                        int wifiPoints = wifi.length();
                        Long secondsAgo = null;
                        try {
                            secondsAgo = fingerprint.getLong("t");
                        } catch (Exception e) {
                            Log.w("WebSocket", e);
                        }

                        assert secondsAgo != null;
                        if ((System.currentTimeMillis() - secondsAgo) / 1000 > 3) {
                            return;
                        }

                        receivedMessages++;

                        String message = "1 second ago: added " + bluetoothPoints + " bluetooth and " + wifiPoints + " wifi points for " + familyName + "/" + deviceName + "\nData Points: " + receivedMessages;
                        oneSecondTimer.resetCounter();
                        if (!locationName.equals("")) {
                            message += " at " + locationName;
                            if (receivedMessages >= 30) { // play sound during learning when 30 is reached
                                playSound();
                            }
                        }
                        TextView rssi_msg = findViewById(R.id.textOutput);
                        Log.d("WebSocket", message);
                        rssi_msg.setText(message);
                    }
                });
            }

            private void playSound() {
                int durationInSeconds = 1;
                ToneGenerator toneGenerator = new ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME);
                toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, durationInSeconds * 1000);
            }

            @Override
            public void onClose(int i, String s, boolean b) {
                Log.i("WebSocket", "Closed " + s);
            }

            @Override
            public void onError(Exception e) {
                Log.i("WebSocket", "Error " + e.getMessage());
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        TextView rssi_msg = findViewById(R.id.textOutput);
                        rssi_msg.setText(R.string.web_socket_error);
                    }
                });
            }
        };
        mWebSocketClient.connect();
    }

    class RemindTask extends TimerTask {
        private Integer counter = 0;

        void resetCounter() {
            counter = 0;
        }

        public void run() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    counter++;
                    if (mWebSocketClient != null) {
                        if (mWebSocketClient.isClosed()) {
                            connectWebSocket();
                        }
                    }
                    TextView rssi_msg = findViewById(R.id.textOutput);
                    String currentText = rssi_msg.getText().toString();
                    if (currentText.contains("ago: ")) {
                        String[] currentTexts = currentText.split("ago: ");
                        currentText = currentTexts[1];
                    }
                    rssi_msg.setText(String.format(Locale.getDefault(), "%d seconds ago: %s", counter, currentText));
                }
            });
        }
    }

}
