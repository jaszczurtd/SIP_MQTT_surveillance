# Diagnostics and verification

[README](../README.md) · [Polski](TROUBLESHOOTING.pl.md) · [Building](BUILDING.md) · [Deployment](DEPLOYMENT.md) · [Usage](USAGE.md)

Check the failing component first: Android build, SIP registration, media,
MQTT connection, or GPIO output. A working component does not confirm the others.

## Common problems

| Symptom | What to check |
|---|---|
| Android build cannot resolve `R.raw.ca` | Add the local `res/raw/ca.crt` described in the build guide; it is absent from Git. |
| Gradle reports an incompatible Java or plugin version | Use JDK 17 and the repository's Gradle wrapper; install SDK 35. |
| GPIO build reports missing `gpiod_line_settings_*` or `gpiod_chip_request_lines` | Check that headers and libraries are libgpiod 2.x, not 1.x. |
| Android MQTT connection fails | Check broker host format, TLS listener on 8883, CA, certificate validity, credentials, and network route. RPi connecting on 1883 does not validate TLS on 8883. |
| MQTT still uses old settings | Stop and reopen the Android app after saving the form. |
| SIP remains red or a call cannot start | Check the phone's account and domain, SIP registration logs, receiver account, and server routing. |
| A call connects but has no picture or sound | Check device selection and permissions, installed media plugins, codecs, and the RTP addresses/ports negotiated through the firewall or VPN. |
| A switch changes but the output does not | Check GPIO client logs, topic permissions, access to `/dev/gpiochip0`, chip/line mapping, and wiring. Broker state is not output feedback. |
| Light or bell turns off after about a minute | This is the GPIO client's timeout; an active call does not periodically extend it. |
| An output turns on after restarting the GPIO client | Check whether the broker retained `on`; a subscription can apply it again. |
| systemd reports a dependency failure | Check the templates' `wg-quick@wg0.service` dependency against your network setup. |
| A service works in a terminal but fails at startup | Check `User`, home directory, credential-file ownership, device access, paths, and binary/library architecture. |

## Logs

Read Raspberry Pi service output:

```bash
journalctl -u sip-receiver.service -u mqtt-gpio.service -n 100 --no-pager
```

If your account cannot read the journal, run the command with `sudo`.
For Android, connect the phone with USB debugging enabled and use Android
Studio Logcat, or run:

```bash
adb logcat -d -s 'SIPClientDebug:V' '*:S'
```

Android startup logs contain MQTT and SIP passwords. Redact them before sharing
the output. The GPIO client logs connections and received commands; the SIP
receiver lists devices, selected devices, registration, and call events.

## Automated checks

After preparing the Android build environment, run from the repository root:

```bash
cd SIPClient
bash ./gradlew :app:testDebugUnitTest
bash ./gradlew :app:connectedDebugAndroidTest
```

The last command requires a connected Android device or emulator. The existing
tests only check `2 + 2` and the application package name. They do not exercise
SIP, MQTT, video, settings persistence, or GPIO. There are no C functional
tests or a repository-wide verification script.

## Check an installation

Perform these steps on the intended devices after [deployment](DEPLOYMENT.md).
They operate the connected light and bell; observe the actual outputs as well
as the app. The Raspberry Pi's existing power/activity LEDs help confirm board
startup, but do not indicate SIP or MQTT readiness.

1. Confirm both app indicators turn green and each Raspberry Pi receiver registers.
2. Call each of **Salon**, **Garaż**, and **Kuchnia**. Confirm video from the
   receiver and audio in both directions. Hang up and confirm the call buttons
   return.
3. Rotate the phone, zoom, pan, and reset the picture during a call.
4. During a garage call, confirm automatic light-on, both switches, explicit
   off commands, and independent automatic switch-off after about 60 seconds.
   Confirm hangup switches the light off and does not immediately switch the bell off.
5. Check MQTT reconnect behavior with and without a garage call. Check GPIO
   restart with retained `on` and `off`; expect `on` to activate the output again.
6. Change account settings, restart the app, and confirm it uses the saved values.
   Restore the intended settings afterwards.
7. Restart configured services and confirm they use saved credentials without
   terminal input. Check network interruption and recovery, including VPN use.

Record the app build, OS/library versions, devices, and observations when
reporting a problem. Passing compilation or the template tests alone does
not establish that an installation passes these checks.
