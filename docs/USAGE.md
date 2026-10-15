# Using the app

[README](../README.md) · [Polski](USAGE.pl.md) · [Deployment](DEPLOYMENT.md) · [Troubleshooting](TROUBLESHOOTING.md)

Configure accounts and permissions first, then check the **SIP** and **MQTT**
status dots. Green means registered with SIP or connected to MQTT, yellow means
a connection is in progress, and red means there is no established connection.
These indicators do not confirm that a camera or GPIO output works.

## Calls and video

Tap **Salon**, **Garaż**, or **Kuchnia** to call the corresponding receiver.
The app requires SIP registration before dialing. The Raspberry Pi receiver
automatically answers, sends its camera video, and enables audio for the call.
The app selects the phone speaker for playback. Tap **Rozłącz** to hang up.

Rotate the phone for portrait or landscape viewing. Pinch to zoom between
1× and 5×, drag to pan, and double-tap or use the reset button to restore the
view. The app keeps the screen awake while its activity is visible.

Use the settings gear to edit MQTT and SIP details. For field formats and
the restart needed after MQTT changes, see [deployment](DEPLOYMENT.md).

## Light and bell

The **Światło** and **Dzwonek** switches appear for calls to the garage account
(`pi3` by default). They send plain-text MQTT commands:

| Switch | Topic | Payload | GPIO line offset |
|---|---|---|---|
| Światło | `gpio/17` | `on` / `off` | `17` |
| Dzwonek | `gpio/27` | `on` / `off` | `27` |

Android publishes with QoS **2** and the retained flag. The GPIO client
subscribes with QoS **0**, accepts `on` and `off` without regard to letter case,
and ignores other commands. Android treats `on` as checked and other payloads
as unchecked, so send only the documented values.

Light commands are also sent automatically when MQTT is available:

| Event | Command |
|---|---|
| Garage call connects | Light `on` |
| Garage call ends, is hung up, or reports an error | Light `off` |
| MQTT connects or reconnects with no current call | Light `off` |
| MQTT connects or reconnects during a garage call | Light `on` |

Ending a call does not automatically send bell `off`. The bell follows its
switch and the GPIO client's timeout. Commands issued while Android is
disconnected from MQTT are not queued by the application.

Each received `on` starts or renews a separate **60-second** timer in the GPIO
client. On expiry, the client sets that output low and publishes retained `off`
with QoS **1**. The timer is checked in a loop with a one-second sleep, so the
switch-off is approximate. An active call does not periodically renew the light
timer. An explicit `off` clears the timer and switches the output off immediately.
This timeout operates while the GPIO program is running; it is not a hardware
watchdog.

The broker retains the last command for each topic and delivers it to new
subscribers. A retained `on` can therefore turn an output on again after the
GPIO client restarts or reconnects, starting a new timer. Android updates its
switches from these messages, but receives no confirmation of the physical
output state. On MQTT connection without a call, its automatic light `off`
also replaces the previously retained light command.

## Network behavior

The app checks whether Android's active network reports Wi-Fi or cellular
transport. It does not test Internet access or reachability of the SIP server
and broker. VPN behavior depends on the active network capabilities reported
by Android. A network check can pass while the services remain unreachable.

Network availability callbacks currently only log changes. MQTT enables Paho's
automatic reconnection, but the app does not provide a complete recovery flow
for every network change. If controls remain hidden after restoring the
network, restart the app and check both connection indicators.
