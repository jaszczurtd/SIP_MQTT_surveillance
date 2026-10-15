# Instalacja i konfiguracja

[README](../README.pl.md) · [English](DEPLOYMENT.md) · [Kompilacja](BUILDING.pl.md) · [Obsługa](USAGE.pl.md)

Przed uruchomieniem klientów przygotuj serwer SIP i broker MQTT. Repozytorium
nie dostarcza tych serwerów. Odbiornik SIP i klient GPIO są osobnymi programami;
mogą działać na jednym Raspberry Pi albo na różnych maszynach.

## Sieć i konta

| Połączenie | Obecna konfiguracja |
|---|---|
| Android → broker MQTT | TLS, `ssl://<broker>:8883`; adres brokera pochodzi z ustawień aplikacji |
| Klient GPIO → broker MQTT | Zwykły TCP, `10.8.0.1:1883`, ustawiony w `gpio.c` |
| Android i odbiornik SIP → serwer SIP | Rejestracja SIP przez UDP; serwer pochodzi z ustawień konta |
| Połączenia SIP przychodzące do odbiornika | Lokalny port SIP ustawiony na `5062` |
| Dźwięk i obraz | Adresy i porty RTP negocjowane podczas połączenia |

Oba porty brokera MQTT muszą udostępniać te same tematy i wiadomości retained.
Skonfiguruj uwierzytelnianie i pozwól klientom odczytywać oraz publikować
`gpio/17` i `gpio/27`. Dodaj CA brokera do zasobów Androida zgodnie z
[instrukcją kompilacji](BUILDING.pl.md). MQTT na Raspberry Pi nie używa TLS;
VPN może chronić ten odcinek sieci. Zmiana ustawień Androida nie zmienia
adresu brokera wpisanego w klienta GPIO. Przy innym adresie lub porcie zmień
[gpio.c](../RaspberryPi/mqtt/gpio.c) i skompiluj go ponownie.

Utwórz konto SIP telefonu i osobne konto każdego odbiornika. Aplikacja wywołuje
`sip:<receiver-user>@<sip-domain>` z następującymi nazwami odbiorników:

| Przycisk aplikacji | Użytkownik SIP |
|---|---|
| Salon | `pi5` |
| Garaż | `pi3` |
| Kuchnia | `pi5-kuchnia` |

Nazwy pochodzą z [Constants.java](../SIPClient/app/src/main/java/com/jaszczurtd/sipclient/Constants.java).
Ich zmiana wymaga ponownej kompilacji aplikacji. Obie strony rejestrują się
w serwerze SIP; telefon wymaga udanej rejestracji przed rozpoczęciem rozmowy.
Odbiornik automatycznie odbiera połączenia i nie ma we własnym kodzie listy
dozwolonych rozmówców. Dostęp do połączeń skonfiguruj na serwerze SIP.

Zezwól klientom na połączenia z portami brokera i skonfigurowanym portem serwera
SIP. Klient GPIO nie potrzebuje przychodzących połączeń MQTT. Rozmowy wymagają
również ruchu SIP do odbiornika i przepływu RTP według parametrów uzgodnionych
w SDP. Źródła nie ustawiają stałego zakresu portów mediów `10001:11000`;
dobierz reguły firewalla do rzeczywistej konfiguracji serwera i Linphone.
Sama rejestracja SIP nie potwierdza, że media przechodzą przez firewall lub VPN.

## Ustawienia Androida

Zainstaluj APK i przyznaj uprawnienia do mikrofonu oraz kamery. Obecna aplikacja
żąda obu i zamyka się po odmowie wymaganego uprawnienia. Przy pierwszym
uruchomieniu wypełnij sześć pól; później otworzysz je ikoną koła zębatego:

| Pole | Wartość |
|---|---|
| Adres brokera | Sama nazwa hosta lub IP, bez `ssl://` i portu; aplikacja dodaje `8883` |
| Użytkownik MQTT | Konto brokera |
| Hasło MQTT | Hasło brokera |
| Domena SIP | Nazwa hosta lub IP serwera SIP, opcjonalnie `host:port`, bez `sip:` |
| Użytkownik SIP | Konto SIP telefonu |
| Hasło SIP | Hasło konta SIP |

Po zmianie ustawień MQTT zatrzymaj aplikację w ustawieniach aplikacji Androida
i uruchom ją ponownie. Zapis formularza nie zastępuje już połączonego klienta MQTT.

Android zapisuje te wartości w zwykłych `SharedPreferences` (`mqtt_prefs`),
bez szyfrowania przez aplikację. Obecne logi startowe zawierają też hasła
MQTT i SIP. Usuń dane logowania przed udostępnieniem logów; zmiana tego
zachowania wymaga poprawki aplikacji.

## Urządzenia Raspberry Pi i pierwsze uruchomienie

Klient GPIO otwiera `/dev/gpiochip0` i zajmuje linie o offsetach **17** i **27**,
aktywne stanem wysokim. Sprawdź, czy ten układ i offsety odpowiadają płytce
oraz systemowi; liczby nie oznaczają fizycznych pozycji pinów na złączu.
Ścieżka i offsety są ustawione w `gpio.c`. Użytkownik procesu musi mieć dostęp
do urządzenia GPIO.

[Odbiornik SIP](../RaspberryPi/sip/linphone_p2p_custom_devices.c) wybiera urządzenia
według fragmentów nazw na liście Linphone: `Usb Audio Device` dla mikrofonu,
`default` dla odtwarzania i `/dev/video0` dla kamery. Potwierdź wybór w logu
startowym. W razie potrzeby zmień te nazwy i skompiluj program ponownie.
Użytkownik procesu potrzebuje dostępu do urządzeń audio i wideo. Odbiornik
włącza dostępny kodek H264; dla dźwięku włącza Opus i Speex przy 8000 Hz,
a pozostałe kodeki audio wyłącza. Dostępność kodeków zależy też od zainstalowanych pluginów.

Przed włączeniem usług zaloguj się jako ich docelowy użytkownik (`pi` w szablonach)
i uruchom potrzebne programy interaktywnie z korzenia repozytorium. Nie wykonuj
tego pierwszego uruchomienia jako root: dane logowania mają trafić do katalogu
domowego użytkownika usługi. Dla 64-bitowego odbiornika SIP:

```bash
mkdir -p "$HOME/.local/share/linphone"
./RaspberryPi/sip/build/sip_receiver_aarch64
```

Dla innej architektury użyj pliku z [instrukcji kompilacji](BUILDING.pl.md).
Podaj użytkownika SIP odbiornika, hasło i domenę serwera (`host:port`).
Potwierdź rejestrację i zakończ program przez Ctrl+C. Następnie uruchom klienta GPIO:

```bash
./RaspberryPi/mqtt/mqtt_gpio
```

Podaj użytkownika i hasło MQTT, potwierdź połączenie i zakończ przez Ctrl+C.
Program steruje rzeczywistymi wyjściami: subskrypcja może natychmiast zastosować
zachowaną komendę retained `on`. Przed tym krokiem przeczytaj
[opis działania wyjść](USAGE.pl.md).

Oba programy pytają o dane, jeśli nie ma ich pliku. Zapisują w `$HOME` pliki
tekstowe z uprawnieniami `0600`: `.sip_client_auth` zawiera użytkownika, hasło
i domenę SIP w osobnych liniach, a `.mqtt_auth` użytkownika i hasło.
Właściciel plików oraz `$HOME` muszą odpowiadać kontu usługi. Aby zmienić dane
logowania Raspberry Pi, zatrzymaj odpowiednią usługę, zmień jej plik jako
ten użytkownik i uruchom usługę ponownie.

## Instalacja usług systemd

Dostarczone pliki są szablonami dla użytkownika `pi` i repozytorium w
`/home/pi/Documents/SIP_MQTT_surveillance`. Przykład poniżej instaluje oba
programy na jednej maszynie. Jeśli działają osobno, na każdej maszynie
instaluj i obsługuj tylko odpowiednią usługę. Z korzenia repozytorium:

```bash
sudo install -m 0644 RaspberryPi/sip/sip-receiver.service /etc/systemd/system/sip-receiver.service
sudo install -m 0644 RaspberryPi/mqtt/mqtt-gpio.service /etc/systemd/system/mqtt-gpio.service
sudoedit /etc/systemd/system/sip-receiver.service /etc/systemd/system/mqtt-gpio.service
```

Przed uruchomieniem popraw te pola w zainstalowanych kopiach:

- Ustaw `User`, `WorkingDirectory` i `ExecStart` zgodnie z kontem, położeniem
  repozytorium i skompilowanym plikiem. W usłudze GPIO ustaw też `Group=gpio`
  na istniejącą grupę z dostępem do wybranego urządzenia GPIO.
- Szablon SIP uruchamia `sip_receiver_aarch64`, lecz ustawia `LD_LIBRARY_PATH`
  na `/usr/lib/arm-linux-gnueabihf`. Przy zwykłych bibliotekach systemowych usuń
  tę linię środowiska albo wskaż właściwy katalog bibliotek dla wybranego pliku.
  Nie łącz bibliotek ARM32 z programem ARM64.
- Oba szablony wymagają `wg-quick@wg0.service` w `After` i `Requires`.
  Zostaw tę zależność tylko przy działającej konfiguracji WireGuard `wg0`.
  Dla innego tunelu zmień zależność; przy pracy wyłącznie w LAN usuń ją
  z obu pól i pozostaw `network-online.target`. Ustaw też osiągalne adresy
  brokera i serwera SIP.

Sprawdź i uruchom skonfigurowane usługi:

```bash
sudo systemd-analyze verify /etc/systemd/system/sip-receiver.service /etc/systemd/system/mqtt-gpio.service
sudo systemctl daemon-reload
sudo systemctl enable --now sip-receiver.service mqtt-gpio.service
systemctl status sip-receiver.service mqtt-gpio.service --no-pager
```

Stan aktywny potwierdza jedynie działanie procesu. Rejestrację, MQTT i media
sprawdź według [procedury weryfikacji](TROUBLESHOOTING.pl.md).
