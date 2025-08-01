package com.jaszczurtd.sipclient;

import static com.jaszczurtd.sipclient.Constants.Connection.*;

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
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.linphone.core.*;

import java.util.Objects;

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
    View sipStatusDot, mqttStatusDot;
    private String sipUser, sipDomain, sipPassword;

    private static final String[] PERMISSIONS = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.INTERNET
    };
    private static final int PERMISSION_REQUEST_CODE = 1;

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

    public void setSipStatus(Connection status) {
        switch (status) {
            case CONN_NONE:
                sipStatusDot.setBackgroundResource(R.drawable.circle_red);
                break;
            case CONN_PROGRESS:
                sipStatusDot.setBackgroundResource(R.drawable.circle_yellow);
                break;
            case CONN_OK:
            default:
                sipStatusDot.setBackgroundResource(R.drawable.circle_green);
                break;
        }
    }

    public void setMQTTStatus(Connection status) {
        switch (status) {
            case CONN_NONE:
                mqttStatusDot.setBackgroundResource(R.drawable.circle_red);
                break;
            case CONN_PROGRESS:
                mqttStatusDot.setBackgroundResource(R.drawable.circle_yellow);
                break;
            case CONN_OK:
            default:
                mqttStatusDot.setBackgroundResource(R.drawable.circle_green);
                break;
        }
    }
    boolean notEmpty(String s) {
        return s != null && !s.isEmpty();
    }

    @SuppressLint("SourceLockedOrientationActivity")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        remoteVideoView = findViewById(R.id.remote_video_surface);
        remoteVideoView.setVisibility(View.GONE);
        callHomeButton = findViewById(R.id.callHomeButton);
        callGarageButton = findViewById(R.id.callGarageButton);
        hangupButton = findViewById(R.id.hangupButton);
        switchLight = findViewById(R.id.switchLight);
        toggleContainer = findViewById(R.id.toggleContainer);
        switchBell = findViewById(R.id.switchBell);
        sipStatusDot = findViewById(R.id.sipStatusDot);
        mqttStatusDot = findViewById(R.id.mqttStatusDot);
        setSipStatus(CONN_NONE);
        setMQTTStatus(CONN_NONE);

        ImageButton settingsButton = findViewById(R.id.settingsButton);
        settingsButton.setOnClickListener(v -> {
            askForSIPandMQTTCredentials(true);
        });

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
            handleNoNetwork(this::finish);
        }

        if (!checkPermissions()) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_REQUEST_CODE);
        }

        prefs = getSharedPreferences(MQTT_CREDENTIALS, MODE_PRIVATE);
        String user = prefs.getString(MQTT_USER, null);
        String pass = prefs.getString(MQTT_PASS, null);
        String ipbroker = prefs.getString(MQTT_BROKER_IP, null);
        sipUser = prefs.getString(SIP_USER, null);
        sipDomain = prefs.getString(SIP_DOMAIN, null);
        sipPassword = prefs.getString(SIP_PASS, null);

        Log.v(TAG, "MQTT credentials: user:" + user + " pass:" + pass + " domain:" + ipbroker);
        Log.v(TAG, "SIP credentials: user:" + sipUser + " pass:" + sipPassword + " domain:" + sipDomain);

        if (notEmpty(user) && notEmpty(pass) && notEmpty(ipbroker) &&
                notEmpty(sipUser) && notEmpty(sipDomain) && notEmpty(sipPassword)) {
            new Thread(() -> {
                runOnUiThread(this::initLinphone);
            }).start();
            new Thread(() -> {
                runOnUiThread(() -> {
                    setupMQTT(user, pass, ipbroker);
                });
            }).start();
        } else {
            askForSIPandMQTTCredentials(false);
        }

        //linphone actions
        callHomeButton.setOnClickListener(v -> makeCall(HOME_USER));
        callGarageButton.setOnClickListener(v -> makeCall(GARAGE_USER));
        hangupButton.setOnClickListener(v -> hangUp());

        //mqtt actions
        lightListener = (btn, isChecked) -> {
            setLightTo(isChecked);
        };
        switchLight.setOnCheckedChangeListener(lightListener);

        bellListener = (btn, isChecked) -> {
            setBellTo(isChecked);
        };
        switchBell.setOnCheckedChangeListener(bellListener);
        toggleContainer.setVisibility(View.GONE);
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
            destroyLinphone();

            Config c = Factory.instance().createConfig(null);
            c.setInt("sip", "inc_timeout", 600);
            c.setInt("sip", "keepalive_period", 30000);

            linphoneCore = Factory.instance().createCoreWithConfig(c, this);

            linphoneCore.setVideoDisplayEnabled(true);
            linphoneCore.setVideoCaptureEnabled(true);
            linphoneCore.setVideoDevice("Camera");

            linphoneCore.setNortpTimeout(600);
            linphoneCore.setUploadBandwidth(0);
            linphoneCore.setDownloadBandwidth(0);
            linphoneCore.setForcedIceRelayEnabled(false);
            linphoneCore.setAdaptiveRateControlEnabled(true);
            linphoneCore.setKeepAliveEnabled(true);

            for (PayloadType pt : linphoneCore.getVideoPayloadTypes()) {
                if ("H264".equals(pt.getMimeType())) {
                    pt.enable(true);
                }
            }

            linphoneCore.setNativeVideoWindowId(remoteVideoView);
            linphoneCore.addListener(new LinphoneListener());

            AuthInfo user = Factory.instance().createAuthInfo(sipUser, null, sipPassword, null, null, sipDomain, null);
            AccountParams accountParams = linphoneCore.createAccountParams();
            String sipAddress = "sip:" + sipUser + "@" + sipDomain;
            Address identity = Factory.instance().createAddress(sipAddress);
            if(identity != null) {
                Log.v(TAG, "login for address " + sipAddress);
                accountParams.setIdentityAddress(identity);
                Address address = Factory.instance().createAddress("sip:" + sipDomain);
                if(address != null) {
                    address.setTransport(TransportType.Udp);
                    accountParams.setServerAddress(address);
                    accountParams.setRegisterEnabled(true);
                }
                Account account = linphoneCore.createAccount(accountParams);
                linphoneCore.addAuthInfo(user);
                linphoneCore.addAccount(account);
                linphoneCore.setDefaultAccount(account);
                linphoneCore.setForcedIceRelayEnabled(true);

            } else {
                Log.e(TAG, "cannot set identity for linphone:" + sipAddress);
            }
            linphoneCore.setUserAgent(TAG, "1.0");

            linphoneCore.start();

        } catch (Exception e) {
            Log.e(TAG, "linphone error:" + e);
            Toast.makeText(this, getString(R.string.linphone_init_error) + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void makeCall(String user) {
        if (!networkMonitor.isConnected()) {
            handleNoNetwork(this::finish);
            return;
        }

        if(!notEmpty(user)) {
            Log.e(TAG, "invalid user for call");
            return;
        }

        try {
            CallParams params = linphoneCore.createCallParams(null);
            Objects.requireNonNull(params).setCameraEnabled(true);
            params.setAudioEnabled(true);
            params.setVideoEnabled(true);

            params.setAvpfEnabled(false);

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

            Address address = Factory.instance().createAddress("sip:" + user + "@" + sipDomain);
            if(address == null) {
                Toast.makeText(this, getString(R.string.linphone_connection_error) + "Invalid user", Toast.LENGTH_SHORT).show();
                return;
            }
            linphoneCore.inviteAddressWithParams(Objects.requireNonNull(address), params);

            callGarageButton.setVisibility(View.GONE);
            callHomeButton.setVisibility(View.GONE);

            hangupButton.setVisibility(View.VISIBLE);

        } catch (Exception e) {
            Log.e(TAG, "linphone error:" + e);
            Toast.makeText(this, getString(R.string.linphone_connection_error) + e.getMessage(), Toast.LENGTH_SHORT).show();
            setLightTo(false);
        }
    }

    void linphoneLogout() {
        Account account = linphoneCore.getDefaultAccount();
        if(account != null) {
            AccountParams accountParams = account.getParams().clone();
            accountParams.setRegisterEnabled(false);
            account.setParams(accountParams);
        }
    }

    private void hangUp() {
        Call call = linphoneCore.getCurrentCall();
        if (call != null) {
            manageMQTTSwitchesVisibility(call, false);
            call.terminate();
        }
        callHomeButton.setVisibility(View.VISIBLE);
        callGarageButton.setVisibility(View.VISIBLE);
        hangupButton.setVisibility(View.GONE);

        toggleContainer.setVisibility(View.GONE);
    }

    void destroyLinphone() {
        try {
            if (linphoneCore != null) {
                linphoneCore.terminateAllCalls();
                linphoneLogout();
                linphoneCore.stop();
                linphoneCore = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "linphone error:" + e);
        }
    }

    @Override
    public void onDestroy() {

        destroyLinphone();
        destroyMQTT();

        try {
            networkMonitor.stopMonitoring();
        } catch (Exception e) {
            Log.e(TAG, "network monitor error:" + e);
        }

        super.onDestroy();
    }

    public class LinphoneListener extends CoreListenerStub {
        @Override
        public void onRegistrationStateChanged(@NonNull Core core, @NonNull ProxyConfig proxyConfig, RegistrationState state, @NonNull String message) {
            runOnUiThread(() -> {
                Log.v(TAG, message);
                switch(state) {
                    case Progress:
                        setSipStatus(CONN_PROGRESS);
                        break;
                    case Ok:
                        setSipStatus(CONN_OK);
                        break;
                    default:
                        setSipStatus(CONN_NONE);
                        break;
                }
            });
        }

        @Override
        public void onCallStateChanged(@NonNull Core core, @NonNull Call call, Call.State state, @NonNull String message) {
            runOnUiThread(() -> {
                switch (state) {
                    case Connected:
                        remoteVideoView.setVisibility(View.VISIBLE);
                        manageMQTTSwitchesVisibility(call, true);
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

                        manageMQTTSwitchesVisibility(call, false);
                        break;
                }
            });
        }
    }

    void setupMQTT(String user, String pass, String ipbroker) {
        if((mqttClient != null && mqttClient.isConnected())) {
            Log.v(TAG, "MQTT client already connected and active");
            return;
        }

        mqttClient = new MQTTClient(
            this,
            ipbroker,
            user, pass,
            (topic, message) -> runOnUiThread(() -> {
                updateSwitchesFromBroker(topic, message);
            }),
            new MQTTClient.MQTTStatusListener() {
                @Override
                public void onConnected() {
                    runOnUiThread(() -> {
                        setMQTTStatus(CONN_OK);
                        Call c = null;
                        if(linphoneCore != null) {
                            c = linphoneCore.getCurrentCall();
                        }

                        manageMQTTSwitchesVisibility(c, true);
                        mqttClient.subscribeTo(MQTT_LIGHTS_TOPIC);
                        mqttClient.subscribeTo(MQTT_BELL_TOPIC);
                    });
                }

                @Override
                public void onProgress() {
                    runOnUiThread(() -> {
                        setMQTTStatus(CONN_PROGRESS);
                    });
                }

                @Override
                public void onDisconnected() {
                    runOnUiThread(() -> {
                        toggleContainer.setVisibility(View.GONE);
                        setMQTTStatus(CONN_NONE);
                    });
                }

                @Override
                public void onConnectionFailed(String reason) {
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, reason, Toast.LENGTH_SHORT).show();
                        setMQTTStatus(CONN_NONE);
                    });
                }
            });
    }

    void destroyMQTT() {
        Log.v(TAG, "destroy MQTT client");

        try {
            switchLight.setOnCheckedChangeListener(null);
            switchBell.setOnCheckedChangeListener(null);

            if(mqttClient != null) {
                mqttClient.stop();
                mqttClient = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "linphone error:" + e);
        }
    }

    void setLightTo(boolean isOn) {
        if(mqttClient != null) {
            mqttClient.publish(MQTT_LIGHTS_TOPIC, isOn ? MQTT_ON : MQTT_OFF, true, () -> { });
        }
    }

    void setBellTo(boolean isOn) {
        if(mqttClient != null) {
            mqttClient.publish(MQTT_BELL_TOPIC, isOn ? MQTT_ON : MQTT_OFF, true, () -> {  });
        }
    }

    void autoFillWidget(EditText t, String identifier) {
        if(t != null && identifier != null) {
            String s = prefs.getString(identifier, null);
            t.setText(notEmpty(s) ? s : "");
        }
    }

    void askForSIPandMQTTCredentials(boolean autofill) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.mqtt_sip_login));

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);

        final EditText ipbroker = new EditText(this);
        ipbroker.setHint(getString(R.string.ipbroker));
        layout.addView(ipbroker);

        final EditText inputUser = new EditText(this);
        inputUser.setHint(getString(R.string.user));
        layout.addView(inputUser);

        final EditText inputPass = new EditText(this);
        inputPass.setHint(getString(R.string.pass));
        inputPass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(inputPass);

        final EditText inputSIPDomain = new EditText(this);
        inputSIPDomain.setHint(getString(R.string.sip_domain));
        layout.addView(inputSIPDomain);

        final EditText inputSIPUser = new EditText(this);
        inputSIPUser.setHint(getString(R.string.sip_user));
        layout.addView(inputSIPUser);

        final EditText inputSIPPass = new EditText(this);
        inputSIPPass.setHint(getString(R.string.sip_pass));
        inputSIPPass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(inputSIPPass);

        if(autofill) {
            autoFillWidget(inputUser, MQTT_USER);
            autoFillWidget(inputPass, MQTT_PASS);
            autoFillWidget(ipbroker, MQTT_BROKER_IP);
            autoFillWidget(inputSIPUser, SIP_USER);
            autoFillWidget(inputSIPDomain, SIP_DOMAIN);
            autoFillWidget(inputSIPPass, SIP_PASS);
        }

        builder.setView(layout);

        builder.setPositiveButton(getString(R.string.ok), (dialog, which) -> {
            String user = inputUser.getText().toString();
            String pass = inputPass.getText().toString();
            String broker = ipbroker.getText().toString();
            sipUser = inputSIPUser.getText().toString();
            sipDomain = inputSIPDomain.getText().toString();
            sipPassword = inputSIPPass.getText().toString();
            prefs.edit()
                    .putString(MQTT_USER, user)
                    .putString(MQTT_PASS, pass)
                    .putString(MQTT_BROKER_IP, broker)
                    .putString(SIP_USER, sipUser)
                    .putString(SIP_PASS, sipPassword)
                    .putString(SIP_DOMAIN, sipDomain)
                    .apply();

            new Thread(() -> {
                runOnUiThread(this::initLinphone);
            }).start();
            new Thread(() -> {
                runOnUiThread(() -> {
                    setupMQTT(user, pass, broker);
                });
            }).start();

        });

        builder.setCancelable(false);
        builder.show();
    }

    void manageMQTTSwitchesVisibility(Call call, boolean state) {
        try {
            if(call != null) {
                Log.v(TAG, "remote address:" + call.getRemoteAddress().getUsername());
                if(Objects.requireNonNull(call.getRemoteAddress().getUsername()).equalsIgnoreCase(GARAGE_USER)) {
                    if(state) {
                        setLightTo(true);
                        toggleContainer.setVisibility(View.VISIBLE);
                    } else {
                        setLightTo(false);
                        toggleContainer.setVisibility(View.GONE);
                    }
                }
            } else {
                setLightTo(false);
                toggleContainer.setVisibility(View.GONE);
            }
        } catch (Exception e) {
            Log.e(TAG, "stwitches visibility problem: " + e);
        }
    }

    void updateSwitchesFromBroker(String topic, MqttMessage message) {
        try {
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
        } catch (Exception e) {
            Log.e(TAG, "update switches from broker problem:" + e);
        }
    }
}