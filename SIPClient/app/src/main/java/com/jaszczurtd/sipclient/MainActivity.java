package com.jaszczurtd.sipclient;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.linphone.core.*;

import java.util.Objects;

@SuppressWarnings("CallToPrintStackTrace")
public class MainActivity extends AppCompatActivity implements Constants {
    AlertDialog alert;
    Core linphoneCore;
    TextureView remoteVideoView;
    Button callHomeButton, callGarageButton, hangupButton;
    SwitchCompat switchLight, switchBell;
    CompoundButton.OnCheckedChangeListener lightListener, bellListener;
    LinearLayout toggleContainer;
    MQTTClient mqttClient;
    NetworkMonitor networkMonitor;
    SharedPreferences prefs;

    private static final String[] PERMISSIONS = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.INTERNET
    };
    private static final int PERMISSION_REQUEST_CODE = 1;

    public static String extractIp(String sipUri) {
        if (sipUri == null) return null;

        int atIndex = sipUri.indexOf('@');
        if (atIndex == -1 || atIndex + 1 >= sipUri.length()) return null;

        return sipUri.substring(atIndex + 1);
    }

    private void handleNoNetwork(Runnable onExitConfirmed) {
        callHomeButton.setVisibility(View.GONE);
        callGarageButton.setVisibility(View.GONE);
        hangupButton.setVisibility(View.GONE);
        toggleContainer.setVisibility(View.GONE);

        alert = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.error))
                .setMessage(getString(R.string.no_internet_error))
                .setPositiveButton(getString(R.string.exit), (dialog, which) -> {
                    alert.dismiss();
                    new Handler(Looper.getMainLooper()).postDelayed(onExitConfirmed, 100);
                })
                .show();
    }

    @SuppressLint("SourceLockedOrientationActivity")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        remoteVideoView = findViewById(R.id.remote_video_surface);
        remoteVideoView.setVisibility(View.GONE);
        callHomeButton = findViewById(R.id.callHomeButton);
        callGarageButton = findViewById(R.id.callGarageButton);
        hangupButton = findViewById(R.id.hangupButton);
        switchLight = findViewById(R.id.switchLight);
        toggleContainer = findViewById(R.id.toggleContainer);
        switchBell = findViewById(R.id.switchBell);

        if (!checkPermissions()) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_REQUEST_CODE);
        } else {
            initLinphone();
        }

        callHomeButton.setOnClickListener(v -> makeCall(HOME_USER));
        callGarageButton.setOnClickListener(v -> makeCall(GARAGE_USER));
        hangupButton.setOnClickListener(v -> hangUp());

        networkMonitor = new NetworkMonitor(this, new NetworkMonitor.NetworkStatusListener() {
            @Override
            public void onConnected() {
                Log.v(TAG, "Internet is connected");
            }

            @Override
            public void onDisconnected() {
                Log.v(TAG, "Internet has been disconnected");
            }
        });

        networkMonitor.startMonitoring();
        if (!networkMonitor.isConnected()) {
            handleNoNetwork(() -> android.os.Process.killProcess(android.os.Process.myPid()));
        }

        prefs = getSharedPreferences(MQTT_CREDENTIALS, MODE_PRIVATE);
        String user = prefs.getString(MQTT_USER, null);
        String pass = prefs.getString(MQTT_PASS, null);
        if (user != null && pass != null) {
            setupMQTT(user, pass);
        } else {
            askForCredentials();
        }
    }

    private boolean checkPermissions() {
        for (String permission : PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, getString(R.string.permissions), Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
            }
            initLinphone();
        }
    }

    private void initLinphone() {
        try {

            Config c = Factory.instance().createConfig(null);
            c.setInt("sip", "inc_timeout", 600);
            c.setInt("sip", "keepalive_period", 30000);
            c.setInt("net", "force_ice_disablement", 0);

            linphoneCore = Factory.instance().createCoreWithConfig(c, this);

            linphoneCore.setVideoDisplayEnabled(true);
            linphoneCore.setVideoCaptureEnabled(true);
            linphoneCore.setVideoDevice("Camera");

            linphoneCore.setNortpTimeout(600);

            for (PayloadType pt : linphoneCore.getVideoPayloadTypes()) {
                if ("H264".equals(pt.getMimeType())) {
                    pt.enable(true);
                }
            }

            linphoneCore.start();
            linphoneCore.setNativeVideoWindowId(remoteVideoView);

            linphoneCore.addListener(new LinphoneListener());

        } catch (Exception e) {
            e.printStackTrace();

            Toast.makeText(this, getString(R.string.linphone_init_error) + e.getMessage(), Toast.LENGTH_LONG).show();

            Log.e(TAG, "linphone error:" + e.getMessage());
        }
    }

    private void makeCall(String user) {
        if (!networkMonitor.isConnected()) {
            handleNoNetwork(() -> android.os.Process.killProcess(android.os.Process.myPid()));
            return;
        }

        try {
            CallParams params = linphoneCore.createCallParams(null);
            Objects.requireNonNull(params).setCameraEnabled(true);
            params.setAudioEnabled(true);
            params.setVideoEnabled(true);

            params.setAvpfEnabled(true);

            AudioDevice[] AudioDevices = linphoneCore.getExtendedAudioDevices();
            for(AudioDevice audioDevice : AudioDevices) {
                Log.i(TAG, "deviceName：" + audioDevice.getDeviceName() + ", driverName：" + audioDevice.getDriverName() +
                        ", id：" + audioDevice.getId() + ", type：" + audioDevice.getType() + ", capabilities：" + audioDevice.getCapabilities());
                if(audioDevice.getType()==AudioDevice.Type.Speaker) {
                    Log.i(TAG, "set output to speaker");
                    params.setOutputAudioDevice(audioDevice);
                    break;
                }
            }

            Address address = Factory.instance().createAddress(user);
            linphoneCore.inviteAddressWithParams(Objects.requireNonNull(address), params);

            callGarageButton.setVisibility(View.GONE);
            callHomeButton.setVisibility(View.GONE);

            hangupButton.setVisibility(View.VISIBLE);

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, getString(R.string.linphone_connection_error) + e.getMessage(), Toast.LENGTH_SHORT).show();
            setLightTo(false);
        }
    }

    private void hangUp() {
        if (linphoneCore.getCurrentCall() != null) {
            linphoneCore.getCurrentCall().terminate();
        }
        callHomeButton.setVisibility(View.VISIBLE);
        callGarageButton.setVisibility(View.VISIBLE);
        hangupButton.setVisibility(View.GONE);

        toggleContainer.setVisibility(View.GONE);
        setLightTo(false);
    }

    @Override
    public void onDestroy() {
        if (linphoneCore != null) {
            linphoneCore.stop();
            linphoneCore = null;
        }

        destroyMQTT();

        networkMonitor.stopMonitoring();

        super.onDestroy();
    }

    public class LinphoneListener extends CoreListenerStub {
        @Override
        public void onCallStateChanged(@NonNull Core core, @NonNull Call call, Call.State state, @NonNull String message) {
            runOnUiThread(() -> {
                switch (state) {
                    case Connected:
                        remoteVideoView.setVisibility(View.VISIBLE);
                        manageMQTTSwitchesVisibility(call);
                        break;
                    case End:
                    case Error:
                        if(state == Call.State.Error) {
                            Toast.makeText(MainActivity.this, getString(R.string.linphone_connection_end) + message, Toast.LENGTH_SHORT).show();
                        }
                        callHomeButton.setVisibility(View.VISIBLE);
                        callGarageButton.setVisibility(View.VISIBLE);
                        hangupButton.setVisibility(View.GONE);
                        remoteVideoView.setVisibility(View.GONE);

                        if(Objects.requireNonNull(call.getRemoteAddress().getDomain()).equalsIgnoreCase(extractIp(GARAGE_USER))) {
                            toggleContainer.setVisibility(View.GONE);
                            setLightTo(false);
                        }
                        break;
                }
            });
        }
    }

    void setupMQTT(String user, String pass) {
        if((mqttClient != null && mqttClient.isConnected())) {
            Log.v(TAG, "MQTT client already connected and active");
            return;
        }

        lightListener = (btn, isChecked) -> {
            setLightTo(isChecked);
        };
        switchLight.setOnCheckedChangeListener(lightListener);

        bellListener = (btn, isChecked) -> {
            setBellTo(isChecked);
        };
        switchBell.setOnCheckedChangeListener(bellListener);

        toggleContainer.setVisibility(View.GONE);

        mqttClient = new MQTTClient(
            this,
            MQTT_BROKER,
            user, pass,
            (topic, message) -> runOnUiThread(() -> {
                updateSwitchesFromBroker(topic, message);
            }),
            new MQTTClient.MQTTStatusListener() {
                @Override
                public void onConnected() {
                    runOnUiThread(() -> {
                        manageMQTTSwitchesVisibility(linphoneCore.getCurrentCall());
                    });
                }

                @Override
                public void onDisconnected() {
                    runOnUiThread(() -> {
                        toggleContainer.setVisibility(View.GONE);
                        Toast.makeText(MainActivity.this, getString(R.string.mqttt_connection_end), Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onConnectionFailed(String reason) {
                    Toast.makeText(MainActivity.this, reason, Toast.LENGTH_SHORT).show();
                }
            });
    }

    void destroyMQTT() {
        Log.v(TAG, "destroy MQTT client");

        switchLight.setOnCheckedChangeListener(null);
        switchBell.setOnCheckedChangeListener(null);

        if(mqttClient != null) {
            mqttClient.stop();
            mqttClient = null;
        }
    }

    void setLightTo(boolean isOn) {
        mqttClient.publish(MQTT_LIGHTS_TOPIC, isOn ? MQTT_ON : MQTT_OFF);
    }

    void setBellTo(boolean isOn) {
        mqttClient.publish(MQTT_BELL_TOPIC, isOn ? MQTT_ON : MQTT_OFF);
    }

    void askForCredentials() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.mqtt_login));

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);

        final EditText inputUser = new EditText(this);
        inputUser.setHint(getString(R.string.user));
        layout.addView(inputUser);

        final EditText inputPass = new EditText(this);
        inputPass.setHint(getString(R.string.pass));
        inputPass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(inputPass);

        builder.setView(layout);

        builder.setPositiveButton(getString(R.string.ok), (dialog, which) -> {
            String user = inputUser.getText().toString();
            String pass = inputPass.getText().toString();
            prefs.edit()
                    .putString(MQTT_USER, user)
                    .putString(MQTT_PASS, pass)
                    .apply();

            setupMQTT(user, pass);
        });

        builder.setCancelable(false);
        builder.show();
    }

    void manageMQTTSwitchesVisibility(Call call) {
        if(call != null) {
            Log.v(TAG, "remote address:" + call.getRemoteAddress().getDomain());
            if(Objects.requireNonNull(call.getRemoteAddress().getDomain()).equalsIgnoreCase(extractIp(GARAGE_USER))) {
                setLightTo(true);
                toggleContainer.setVisibility(View.VISIBLE);
            }
        }
    }

    void updateSwitchesFromBroker(String topic, MqttMessage message) {
        Log.v(TAG, "Broker update: " + topic + " message:" + message.toString());

        boolean isOn = new String(message.getPayload()).equalsIgnoreCase(MQTT_ON);
        if (topic.equals(MQTT_LIGHTS_TOPIC)) {
            if(switchLight.isChecked() != isOn) {
                Log.v(TAG, "set lights to:" + isOn);

                switchLight.setOnCheckedChangeListener(null);
                switchLight.setChecked(isOn);
                switchLight.setOnCheckedChangeListener(lightListener);
            }
        }
        if (topic.equals(MQTT_BELL_TOPIC)) {
            if(switchBell.isChecked() != isOn) {
                Log.v(TAG, "set bell to:" + isOn);

                switchBell.setOnCheckedChangeListener(null);
                switchBell.setChecked(isOn);
                switchBell.setOnCheckedChangeListener(bellListener);
            }
        }

    }
}