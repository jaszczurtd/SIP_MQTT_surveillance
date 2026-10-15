# Deployment

[README](../README.md) · [Polski](DEPLOYMENT.pl.md) · [Building](BUILDING.md) · [Usage](USAGE.md)

Prepare the SIP server and MQTT broker before starting the clients. Neither
server is supplied by this repository. The SIP receiver and GPIO client are
separate programs and may run on the same Raspberry Pi or different machines.

## Network and accounts

| Connection | Current configuration |
|---|---|
| Android → MQTT broker | TLS, `ssl://<broker>:8883`; broker host comes from app settings |
| GPIO client → MQTT broker | Plain TCP, `10.8.0.1:1883`, set in `gpio.c` |
| Android and SIP receiver → SIP server | SIP registration over UDP; server comes from account settings |
| Incoming SIP at the receiver | Local SIP port configured as `5062` |
| Audio/video | RTP addresses and ports negotiated during the call |

Both MQTT listeners must expose the same topics and retained messages. Configure
broker authentication and allow the clients to read and publish `gpio/17` and
`gpio/27`. Supply the broker CA to the Android build as described in
[building](BUILDING.md). MQTT on the Raspberry Pi has no TLS; a VPN can protect
that network path. Changing Android settings does not change the GPIO client's
hardcoded broker address: edit [gpio.c](../RaspberryPi/mqtt/gpio.c) and rebuild
when using a different address or port.

Create a SIP account for the phone and a separate account for each receiver.
The app calls `sip:<receiver-user>@<sip-domain>` with these receiver names:

| App button | SIP username |
|---|---|
| Salon | `pi5` |
| Garaż | `pi3` |
| Kuchnia | `pi5-kuchnia` |

These names come from [Constants.java](../SIPClient/app/src/main/java/com/jaszczurtd/sipclient/Constants.java).
Changing them requires rebuilding the app. Both ends register with the SIP
server; the phone requires successful registration before placing a call.
The receiver automatically answers incoming calls and has no caller allowlist
in its own code. Configure call access on the SIP server.

Allow client connections to the broker listeners and the SIP server's configured
port. The GPIO client does not need an inbound MQTT listener. For calls, allow
the receiver's SIP traffic and the RTP paths negotiated in SDP. The source
does not configure a fixed `10001:11000` media range; choose firewall rules
from the actual server and Linphone configuration. SIP registration alone does
not establish that media can cross the firewall or VPN.

## Android settings

Install the APK and grant microphone and camera permissions. The current app
requests both and exits if a requested permission is denied. Enter all six
fields on first launch, or open the settings gear to edit them:

| Field | Value |
|---|---|
| Broker address | Hostname or IP only, without `ssl://` or a port; the app adds `8883` |
| MQTT username | Broker account |
| MQTT password | Broker password |
| SIP domain | SIP server hostname or IP, optionally `host:port`, without `sip:` |
| SIP username | The phone's SIP account |
| SIP password | SIP account password |

After changing MQTT settings, stop the app through Android's app settings and
launch it again. Saving the form does not replace an already connected MQTT client.

Android stores these values in ordinary `SharedPreferences` (`mqtt_prefs`),
without application-level encryption. The current startup logs also include
MQTT and SIP passwords. Remove credentials before sharing logs; changing this
behavior requires an application change.

## Raspberry Pi devices and first launch

The GPIO client opens `/dev/gpiochip0` and requests line offsets **17** and
**27**, active high. Check that this chip and its line offsets match your board
and OS; these numbers are not physical header pin positions. The paths and
offsets are set in `gpio.c`. The process user needs access to the GPIO device.

The [SIP receiver](../RaspberryPi/sip/linphone_p2p_custom_devices.c) selects
devices by substrings in Linphone's device list: `Usb Audio Device` for capture,
`default` for playback, and `/dev/video0` for the camera. Read its startup output
to confirm the selected devices. Change those selectors and rebuild if needed.
The process user needs access to the audio and video devices. H264 is enabled
when available; the receiver enables Opus and Speex at 8000 Hz for audio and
disables other audio codecs. Codec availability also depends on installed plugins.

Before enabling services, log in as their intended user (`pi` in the templates)
and run each required program interactively from the repository root. Do not
run this initial setup as root: credentials belong in the service user's home.
For a 64-bit SIP build:

```bash
mkdir -p "$HOME/.local/share/linphone"
./RaspberryPi/sip/build/sip_receiver_aarch64
```

Use the corresponding binary from [building](BUILDING.md) for another architecture.
Enter the receiver's SIP username, password, and server domain (`host:port`).
Confirm registration, then stop with Ctrl+C. For the GPIO client:

```bash
./RaspberryPi/mqtt/mqtt_gpio
```

Enter the MQTT username and password, confirm connection, and stop with Ctrl+C.
This program drives real outputs: subscribing can immediately apply a retained
`on` command. Read the [output behavior](USAGE.md) before this step.

Both programs prompt when their credential file is absent. They write plaintext
files with mode `0600` under `$HOME`: `.sip_client_auth` contains username,
password, and SIP domain on separate lines; `.mqtt_auth` contains username and
password. Keep ownership and `$HOME` consistent with the service account.
To change Raspberry Pi credentials, stop the corresponding service, edit its
file as that user, and restart the service.

## Install systemd services

The supplied files are templates for user `pi` and a checkout under
`/home/pi/Documents/SIP_MQTT_surveillance`. The example below installs both
programs on one machine. If they run separately, install and operate only the
corresponding service on each host. From the repository root:

```bash
sudo install -m 0644 RaspberryPi/sip/sip-receiver.service /etc/systemd/system/sip-receiver.service
sudo install -m 0644 RaspberryPi/mqtt/mqtt-gpio.service /etc/systemd/system/mqtt-gpio.service
sudoedit /etc/systemd/system/sip-receiver.service /etc/systemd/system/mqtt-gpio.service
```

Before starting the services, adjust these fields in the installed copies:

- Set `User`, `WorkingDirectory`, and `ExecStart` to your account, checkout,
  and built binary. For GPIO, also set `Group=gpio` to a group that exists
  and has access to the selected GPIO device.
- The SIP template runs `sip_receiver_aarch64` but sets `LD_LIBRARY_PATH`
  to `/usr/lib/arm-linux-gnueabihf`. Remove this environment line when using
  normal system libraries, or replace it with the correct library directory
  for the chosen binary. Do not mix ARM32 libraries with an ARM64 executable.
- Both templates require `wg-quick@wg0.service` in `After` and `Requires`.
  Keep this dependency only with a working WireGuard `wg0` configuration.
  For another tunnel, change the dependency; for LAN-only use, remove it from
  both fields and keep `network-online.target`. Also configure reachable
  broker and SIP server addresses.

Check and start the configured units:

```bash
sudo systemd-analyze verify /etc/systemd/system/sip-receiver.service /etc/systemd/system/mqtt-gpio.service
sudo systemctl daemon-reload
sudo systemctl enable --now sip-receiver.service mqtt-gpio.service
systemctl status sip-receiver.service mqtt-gpio.service --no-pager
```

An active unit only confirms that its process is running. Check registration,
MQTT connectivity, and media using the [verification procedure](TROUBLESHOOTING.md).
