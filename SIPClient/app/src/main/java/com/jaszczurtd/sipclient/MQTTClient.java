package com.jaszczurtd.sipclient;

import static org.eclipse.paho.client.mqttv3.MqttException.REASON_CODE_CLIENT_CONNECTED;

import android.content.Context;
import android.util.Log;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

public class MQTTClient implements Constants {
    private MqttClient client;

    private final MQTTClient.MQTTStatusListener connectionCallback;
    private final IMqttMessageListener MQTTlistener;

    public interface MQTTStatusListener {
        void onConnected();
        void onDisconnected();
        void onConnectionFailed(String reason);
    }

    public interface MQTTMessageDelivered {
        void onMessageDelivered();
    }

    private MQTTMessageDelivered dc;
    private final String username;
    private final String password;

    public MQTTClient(Context context, String broker, String username, String password,
                      IMqttMessageListener listener, MQTTStatusListener connectionListener) {

        connectionCallback = connectionListener;
        MQTTlistener = listener;
        this.username = username;
        this.password = password;

        try {
            String clientId = TAG + System.currentTimeMillis();
            Log.v(TAG, "clientID:" + clientId);

            client = new MqttClient(broker, clientId, null);
            client.setCallback(new MqttCallbackExtended() {
                @Override
                public void connectComplete(boolean reconnect, String serverURI) {
                    Log.v(TAG, "MQTT connected: " + serverURI + (reconnect ? " (again)" : ""));
                    if(connectionCallback != null) {
                        connectionCallback.onConnected();
                    }
                }

                @Override
                public void connectionLost(Throwable cause) {
                    Log.v(TAG, "MQTT connection lost: " + cause);
                    if(connectionCallback != null) {
                        connectionCallback.onDisconnected();
                    }
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    Log.v(TAG, "MQTT message has been received: " + topic + " -> " + new String(message.getPayload()));
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    Log.v(TAG, "MQTT message has been delivered");
                    if(dc != null) {
                        dc.onMessageDelivered();
                        dc = null;
                    }
                }
            });
            connect();

        } catch (Exception e) {
            Log.e(TAG, "Error MQTT client create: " + e);
        }
    }

    public void subscribeTo(String topic) {
        if (MQTTlistener == null) {
            Log.e(TAG, "Error: subscribeTo -> MQTTlistener is null");
            return;
        }

        if (isConnected()) {
            try {
                unsubscribeFrom(topic);
                Log.v(TAG, "subscribe to: " + topic);
                client.subscribe(topic, MQTTlistener);

            } catch (MqttException e) {
                String reason = e.getReasonCode() + " - " + e;
                Log.e(TAG, "Error MQTT subscription: " + reason);
            }
        } else {
            Log.e(TAG, "Error MQTT subscription: disconnected");
        }
    }

    public void unsubscribeFrom(String topic) {
        if (isConnected()) {
            try {
                client.unsubscribe(topic);
            } catch (MqttException e) {
                String reason = e.getReasonCode() + " - " + e;
                Log.e(TAG, "Error MQTT unsubscription: " + reason);
            }
        } else {
            Log.e(TAG, "Error MQTT unsubscribeFrom: disconnected");
        }
    }

    void disconnect() {
        if (isConnected()) {
            try {
                Log.v(TAG, "MQTT: disconnect from client");
                client.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Error MQTT client disconnect: " + e);
            }
        }
    }

    void connect() {
        try {
            MqttConnectOptions options = new MqttConnectOptions();
            options.setUserName(username);
            options.setPassword(password.toCharArray());
            options.setAutomaticReconnect(true);
            options.setCleanSession(false);

            client.connect(options);
        } catch (MqttException e) {
            if(e.getReasonCode() != REASON_CODE_CLIENT_CONNECTED) {
                String reason = e.getReasonCode() + " - " + e;
                Log.e(TAG, "Error MQTT connection: " + reason);
                if (connectionCallback != null) {
                    connectionCallback.onConnectionFailed(reason);
                }
            }
        }
    }

    void stop() {
        if (isConnected()) {
            try {
                client.disconnect();
                client.close();
            } catch (Exception e) {
                Log.e(TAG, "Error MQTT client stop: " + e);
            }
        }
    }

    public void publish(String topic, String payload, boolean retained, MQTTMessageDelivered deliveryCallback) {
        if (isConnected()) {
            try {
                dc = deliveryCallback;
                MqttMessage message = new MqttMessage(payload.getBytes());
                message.setQos(2);
                message.setRetained(retained);
                client.publish(topic, message);
            } catch (MqttException e) {
                Log.e(TAG, "Error MQTT publish: " + e);
            }
        } else {
            Log.e(TAG, "Error MQTT publish: disconnected");
        }
    }

    public boolean isConnected() {
        return client != null && client.isConnected();
    }
}
