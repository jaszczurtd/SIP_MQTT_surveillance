# Building

[README](../README.md) · [Polski](BUILDING.pl.md) · [Deployment](DEPLOYMENT.md)

Build Android on your development computer and the C programs on a Linux
system matching the Raspberry Pi architecture. Commands below start
in the repository root unless stated otherwise.

## Android

The project uses Android Gradle Plugin **8.10.1** and the Gradle **8.11.1**
wrapper. Use **JDK 17**, Android SDK Platform **35**, and Build Tools **35.0.0**.
These toolchain requirements follow the
[AGP 8.10 documentation](https://developer.android.com/build/releases/agp-8-10-0-release-notes).
Choose Android Studio with support for AGP 8.10 using the
[IDE compatibility table](https://developer.android.com/studio/releases#android_gradle_plugin_and_android_studio_compatibility).

The app requires Android **7.0 / API 24** or newer and targets API **35**.
Java source/target compatibility and Kotlin JVM target are **11**; this is
separate from the JDK used to run Gradle. Dependency declarations select
Linphone SDK **5.4.+** and Paho **1.2.5**. The Linphone patch version can vary
between dependency resolutions.

1. Open `SIPClient` in Android Studio and configure the SDK location and Gradle JDK.
   For command-line builds, configure `JAVA_HOME` for JDK 17 and the SDK path
   through `ANDROID_HOME` or `SIPClient/local.properties` (`sdk.dir`).
2. Obtain the public CA certificate that signs the MQTT broker certificate.
   Create `SIPClient/app/src/main/res/raw` if needed and save the certificate
   as `ca.crt` in that directory. The app loads it as `R.raw.ca`.
   Git ignores this local file, so a fresh checkout needs it before compilation.
   Keep the CA private key out of the app.
3. Build the debug APK:

```bash
cd SIPClient
bash ./gradlew :app:assembleDebug
```

On Windows, run from `SIPClient`:

```powershell
.\gradlew.bat :app:assembleDebug
```

The result is `SIPClient/app/build/outputs/apk/debug/app-debug.apk`, relative
to the repository root. Android Studio can install it on a connected device;
with Android SDK Platform Tools, run from the repository root:

```bash
adb install -r SIPClient/app/build/outputs/apk/debug/app-debug.apk
```

The first build needs access to the configured Google, Maven Central, and
Linphone Maven repositories. Runtime account settings are described in
[deployment](DEPLOYMENT.md).

## MQTT GPIO client

The client compiles `RaspberryPi/mqtt/gpio.c` and links libmosquitto and
**libgpiod 2.x**. The 1.x GPIO API is incompatible with this source.
On Debian-based systems, install the development packages:

```bash
sudo apt install build-essential pkg-config libmosquitto-dev libgpiod-dev
pkg-config --modversion libgpiod
pkg-config --atleast-version=2.0 libgpiod
```

Check the version before building. For example, Debian trixie's
[libgpiod-dev package](https://packages.debian.org/trixie/libgpiod-dev)
provides the 2.x API. If your distribution provides 1.x, prepare a compatible
library and headers first; installing a package with the same name does not
resolve the API difference.

```bash
make -C RaspberryPi/mqtt
```

The output is `RaspberryPi/mqtt/mqtt_gpio`. The Makefile already supplies
`-lmosquitto -lgpiod`. Configure the broker address and GPIO device
[before running it](DEPLOYMENT.md).

## SIP receiver

Install headers and libraries for liblinphone, mediastreamer2, bctoolbox,
and oRTP. Debian lists these development dependencies under
[liblinphone-dev](https://packages.debian.org/trixie/liblinphone-dev) and
[libmediastreamer-dev](https://packages.debian.org/trixie/libmediastreamer-dev).
On Debian-based systems:

```bash
sudo apt install build-essential liblinphone-dev libmediastreamer-dev libbctoolbox-dev libortp-dev
```

Choose a target matching the installed userspace and libraries, rather than
the Raspberry Pi model. These commands use the local `gcc`:

| Build system | Command from the repository root | Output under `RaspberryPi/sip/build/` |
|---|---|---|
| ARM 64-bit userspace | `make -C RaspberryPi/sip m64 CC_AARCH64=gcc` | `sip_receiver_aarch64` |
| ARM 32-bit hard-float userspace | `make -C RaspberryPi/sip m32 CC_ARMHF=gcc` | `sip_receiver_armhf` |
| Linux Mint / Debian-style x86-64 | `make -C RaspberryPi/sip mint` | `sip_receiver_mint` |

Plain `make` only selects the build-directory target. Always select `m32`,
`m64`, or `mint`. Without the compiler overrides, the ARM targets use
`arm-linux-gnueabihf-gcc` and `aarch64-linux-gnu-gcc`. Cross-compilation also
requires matching target headers and libraries; the Makefile does not prepare
a sysroot or install them.

The C receiver uses the system Linphone libraries, independently of the Android
SDK version. The repository does not pin their package versions or provide
a tested distribution matrix. Camera, audio, account, and service setup is
covered in [deployment](DEPLOYMENT.md).
