# Kompilacja

[README](../README.pl.md) · [English](BUILDING.md) · [Instalacja](DEPLOYMENT.pl.md)

Aplikację Android skompiluj na swojej maszynie developerskiej (windows / linux), 
a programy C na Linuksie z architekturą zgodną z Raspberry Pi. Poniższe polecenia zaczynają się w korzeniu repozytorium, chyba że opis wskazuje inny katalog.

## Android

Projekt używa Android Gradle Plugin **8.10.1** i wrappera Gradle **8.11.1**.
Przygotuj **JDK 17**, Android SDK Platform **35** i Build Tools **35.0.0**.
Wymagania narzędzi wynikają z
[dokumentacji AGP 8.10](https://developer.android.com/build/releases/agp-8-10-0-release-notes).
Wersję Android Studio obsługującą AGP 8.10 wybierz według
[tabeli zgodności IDE](https://developer.android.com/studio/releases#android_gradle_plugin_and_android_studio_compatibility).

Aplikacja wymaga Androida **7.0 / API 24** lub nowszego i ma target API **35**.
Zgodność źródeł i kodu wynikowego Java oraz Kotlin JVM target mają wartość **11**;
to osobne ustawienia od JDK uruchamiającego Gradle. Zależności wskazują Linphone
SDK **5.4.+** i Paho **1.2.5**. Wersja poprawkowa Linphone może się zmieniać
przy ponownym pobieraniu zależności.

1. Otwórz `SIPClient` w Android Studio i ustaw ścieżkę SDK oraz Gradle JDK.
   Przy kompilacji z terminala ustaw `JAVA_HOME` na JDK 17, a ścieżkę SDK
   przez `ANDROID_HOME` lub wpis `sdk.dir` w `SIPClient/local.properties`.
2. Pobierz publiczny certyfikat CA, który podpisał certyfikat brokera MQTT.
   Utwórz katalog `SIPClient/app/src/main/res/raw`, jeśli go nie ma, i zapisz
   w nim certyfikat jako `ca.crt`. Aplikacja odczytuje go przez `R.raw.ca`.
   Git ignoruje ten lokalny plik, więc świeże drzewo wymaga jego dodania przed
   kompilacją. Klucz prywatny CA nie należy do zasobów aplikacji.
3. Skompiluj debug APK:

```bash
cd SIPClient
bash ./gradlew :app:assembleDebug
```

W Windows uruchom z katalogu `SIPClient`:

```powershell
.\gradlew.bat :app:assembleDebug
```

Wynik to `SIPClient/app/build/outputs/apk/debug/app-debug.apk`, licząc od korzenia
repozytorium. Android Studio może zainstalować go na podłączonym urządzeniu.
Jeśli masz Android SDK Platform Tools, możesz też uruchomić z korzenia repozytorium:

```bash
adb install -r SIPClient/app/build/outputs/apk/debug/app-debug.apk
```

Pierwsza kompilacja wymaga dostępu do skonfigurowanych repozytoriów Google,
Maven Central i Linphone Maven. Ustawienia kont opisuje
[instrukcja instalacji](DEPLOYMENT.pl.md).

## Klient MQTT sterujący GPIO

Klient kompiluje `RaspberryPi/mqtt/gpio.c` i korzysta z libmosquitto oraz
**libgpiod 2.x**. API GPIO w wersji 1.x jest niezgodne z tym źródłem.
W systemach opartych na Debianie zainstaluj pakiety deweloperskie:

```bash
sudo apt install build-essential pkg-config libmosquitto-dev libgpiod-dev
pkg-config --modversion libgpiod
pkg-config --atleast-version=2.0 libgpiod
```

Sprawdź wersję przed kompilacją. Przykładowo
[pakiet libgpiod-dev w Debianie trixie](https://packages.debian.org/trixie/libgpiod-dev)
udostępnia API 2.x. Jeśli dystrybucja dostarcza 1.x, najpierw przygotuj zgodną
bibliotekę i nagłówki. Samo zainstalowanie pakietu o tej samej nazwie nie
usuwa różnicy API.

```bash
make -C RaspberryPi/mqtt
```

Wynik to `RaspberryPi/mqtt/mqtt_gpio`. Makefile przekazuje już
`-lmosquitto -lgpiod`. Przed uruchomieniem
[ustaw adres brokera i urządzenie GPIO](DEPLOYMENT.pl.md).

## Odbiornik SIP

Zainstaluj nagłówki i biblioteki liblinphone, mediastreamer2, bctoolbox
oraz oRTP. Debian wymienia te zależności deweloperskie przy pakietach
[liblinphone-dev](https://packages.debian.org/trixie/liblinphone-dev) i
[libmediastreamer-dev](https://packages.debian.org/trixie/libmediastreamer-dev).
W systemach opartych na Debianie:

```bash
sudo apt install build-essential liblinphone-dev libmediastreamer-dev libbctoolbox-dev libortp-dev
```

Wybierz target zgodny z architekturą zainstalowanego środowiska użytkownika
i bibliotek, a nie z modelem Raspberry Pi. Te polecenia używają lokalnego `gcc`:

| System kompilacji | Polecenie z korzenia repozytorium | Wynik w `RaspberryPi/sip/build/` |
|---|---|---|
| ARM, środowisko 64-bitowe | `make -C RaspberryPi/sip m64 CC_AARCH64=gcc` | `sip_receiver_aarch64` |
| ARM, środowisko 32-bitowe hard-float | `make -C RaspberryPi/sip m32 CC_ARMHF=gcc` | `sip_receiver_armhf` |
| Linux Mint / układ katalogów Debiana, x86-64 | `make -C RaspberryPi/sip mint` | `sip_receiver_mint` |

Samo `make` wybiera jedynie target tworzący katalog wynikowy. Zawsze podawaj
`m32`, `m64` albo `mint`. Bez nadpisania kompilatora targety ARM używają
`arm-linux-gnueabihf-gcc` i `aarch64-linux-gnu-gcc`. Kompilacja skrośna wymaga
też nagłówków i bibliotek dla docelowej architektury; Makefile nie przygotowuje
sysroot ani ich nie instaluje.

Odbiornik C korzysta z systemowych bibliotek Linphone, niezależnie od wersji
SDK Androida. Repozytorium nie przypina wersji tych pakietów ani nie zawiera
macierzy sprawdzonych dystrybucji. Konfigurację kamery, dźwięku, kont i usług
opisuje [instrukcja instalacji](DEPLOYMENT.pl.md).
