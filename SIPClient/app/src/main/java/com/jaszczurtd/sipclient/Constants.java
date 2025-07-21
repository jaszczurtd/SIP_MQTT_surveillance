package com.jaszczurtd.sipclient;

public interface Constants {
    static final String TAG = "SIPClientDebug";
    static final String HOME_USER = "sip:pi@10.8.0.1";
    static final String GARAGE_USER = "sip:pi@10.8.0.2";
    static final String MQTT_BROKER = "tcp://10.8.0.1:1883";
    static final String MQTT_CREDENTIALS = "mqtt_prefs";
    static final String MQTT_USER = "mqtt_user";
    static final String MQTT_PASS = "mqtt_pass";
    static final String MQTT_LIGHTS_TOPIC = "gpio/17";
    static final String MQTT_BELL_TOPIC = "gpio/27";
    static final String MQTT_ON = "on";
    static final String MQTT_OFF = "off";

}
