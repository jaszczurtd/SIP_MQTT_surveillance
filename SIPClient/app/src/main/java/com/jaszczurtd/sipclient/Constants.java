package com.jaszczurtd.sipclient;

public interface Constants {
    String TAG = "SIPClientDebug";
    String HOME_USER = "sip:pi@10.8.0.1";
    String GARAGE_USER = "sip:pi@10.8.0.2";
    String MQTT_BROKER = "tcp://10.8.0.1:1883";
    String MQTT_CREDENTIALS = "mqtt_prefs";
    String MQTT_USER = "mqtt_user";
    String MQTT_PASS = "mqtt_pass";
    String MQTT_LIGHTS_TOPIC = "gpio/17";
    String MQTT_BELL_TOPIC = "gpio/27";
    String MQTT_ON = "on";
    String MQTT_OFF = "off";

}
