# SIPClient – Android App for Video Intercom & MQTT-based Home Control

**SIPClient** is an Android application that enables video communication with home intercom-like devices based on Raspberry pi 3&5 over SIP (Linphone) and allows remote control of GPIO (light, bell) on Raspberry Pi 3 using MQTT. It's designed to work in a local or VPN-based network (e.g. WireGuard).

---

## 📸 Features

- 📞 SIP-based video calls with Linphone Core SDK
- 🏠 Dedicated buttons for connecting to `home` and `garage` devices
- 💡 Remote control of light and bell via MQTT (GPIO 17 and 27)
- 🔁 Automatic state synchronization via retained MQTT messages
- 🔐 MQTT authentication with local credential storage
- 📶 Network-aware behavior: connects only when a valid network is available
- 📱 Optimized layout for mobile portrait mode

---

## 🧰 Architecture Overview

    +---------------------+                          +------------------+
    |      Android App    |                          |  Raspberry Pi 5  |
    |---------------------|                          |------------------|
    | SIP (Linphone)      |<------ SIP over VPN ---->| linphone-daemon  |
    | MQTT (Paho)         |                          | Mosquitto Broker |
    +---------------------+                          +------------------+
                ^
                |
                | MQTT (tcp://10.8.0.1)
                | SIP over VPN
                v
    +---------------------+
    |  Raspberry Pi 3     |
    |---------------------|
    | MQTT subscriber     |
    | linphone-daemon     |
    | GPIO 17 -> light    |
    | GPIO 27 -> bell     |
    +---------------------+

---

## 🗝️ MQTT Topics

| Topic       | Description         | Retained | Type      |
|-------------|---------------------|----------|-----------|
| `gpio/17`   | Light control        | ✅ Yes    | `on`/`off` |
| `gpio/27`   | Bell control         | ✅ Yes    | `on`/`off` |

---

## 🔐 MQTT Credentials

On first app launch (with internet access), user is prompted to enter:
- Username
- Password

These credentials are securely stored using `SharedPreferences` and reused automatically.

> 🔒 Credentials are **not hardcoded** in the source code.

---

## 🧠 Logic Summary

- Pressing **Call Home** or **Call Garage** in Android app initiates a SIP call.
- When the call is established with `garage` (10.8.0.2), UI shows **light** and **bell** switches.
- Switch state changes are published to MQTT and retained.
- On app start, retained messages restore UI state automatically.

---

## 📦 Dependencies

- [Linphone SDK](https://linphone.org)
- [Eclipse Paho MQTT Client](https://www.eclipse.org/paho/)

---

## ✅ Permissions Required

- `INTERNET`
- `RECORD_AUDIO`
- `CAMERA`

---

## 🚀 Build & Run

### Prerequisites:
- Android Studio (Arctic Fox or newer)
- SDK 31+
- Network access to MQTT broker and SIP devices (typically via WireGuard VPN)

note: pi3 MQTT client has to be run at least once from the console, in order to set MQTT user & password.

📄 License

This project is released under the MIT License.
