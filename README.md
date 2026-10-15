# SIPClient — video intercom and MQTT controls

[Polski](README.pl.md)

SIPClient connects an Android phone to Raspberry Pi cameras through SIP and
controls a light and a bell through MQTT. The app has three call buttons:
**Salon**, **Garaż**, and **Kuchnia**. Video supports portrait and landscape
orientation, pinch zoom, panning, and a reset button.

The repository contains the [Android app](SIPClient), a
[SIP receiver](RaspberryPi/sip) that automatically answers calls, and an
[MQTT GPIO client](RaspberryPi/mqtt). Provide a SIP server with accounts and
an MQTT broker separately. The components can communicate over a local
network or a VPN; the supplied Raspberry Pi configuration uses WireGuard addresses.

Light and bell controls appear during garage calls. The app also switches
the light automatically, and the GPIO client turns each output off after
about 60 seconds. See [usage](docs/USAGE.md) before connecting the outputs.

## Set up the system

1. [Build the app and Raspberry Pi programs](docs/BUILDING.md), including
   the broker CA certificate required by the Android build.
2. [Configure accounts, devices, networking, and services](docs/DEPLOYMENT.md).
3. [Use the app](docs/USAGE.md) and follow the
   [diagnostics and verification procedure](docs/TROUBLESHOOTING.md).

The Android app uses Linphone SDK and Eclipse Paho. The Raspberry Pi programs
use liblinphone, libmosquitto, and libgpiod. Build requirements and dependency
versions are listed in the build guide.

## License

The MIT license. Dependencies have their own licenses.
