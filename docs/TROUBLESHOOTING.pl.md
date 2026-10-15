# Diagnostyka i sprawdzanie działania

[README](../README.pl.md) · [English](TROUBLESHOOTING.md) · [Kompilacja](BUILDING.pl.md) · [Instalacja](DEPLOYMENT.pl.md) · [Obsługa](USAGE.pl.md)

Zacznij od elementu, który sprawia problem: kompilacji Androida, rejestracji
SIP, mediów, połączenia MQTT albo wyjścia GPIO. Działanie jednego nie potwierdza pozostałych.

## Typowe problemy

| Objaw | Co sprawdzić |
|---|---|
| Kompilacja Androida nie znajduje `R.raw.ca` | Dodaj lokalny `res/raw/ca.crt` opisany w instrukcji kompilacji; Git go nie zawiera. |
| Gradle zgłasza niezgodną wersję Java lub pluginu | Użyj JDK 17 i wrappera Gradle z repozytorium; zainstaluj SDK 35. |
| Kompilacja GPIO zgłasza brak `gpiod_line_settings_*` lub `gpiod_chip_request_lines` | Sprawdź, czy nagłówki i biblioteki pochodzą z libgpiod 2.x, a nie 1.x. |
| Android nie łączy się z MQTT | Sprawdź format adresu brokera, TLS na porcie 8883, CA, ważność certyfikatu, dane logowania i trasę sieciową. Połączenie RPi na 1883 nie potwierdza działania TLS na 8883. |
| MQTT nadal używa starych ustawień | Po zapisaniu formularza zatrzymaj aplikację Android i uruchom ją ponownie. |
| SIP pozostaje czerwony lub nie można rozpocząć rozmowy | Sprawdź konto i domenę telefonu, logi rejestracji SIP, konto odbiornika i routing serwera. |
| Rozmowa się łączy, ale brakuje obrazu lub dźwięku | Sprawdź wybór urządzeń, uprawnienia, pluginy mediów, kodeki oraz negocjowane adresy i porty RTP przechodzące przez firewall lub VPN. |
| Przełącznik zmienia stan, ale wyjście nie reaguje | Sprawdź logi klienta GPIO, uprawnienia do tematów, dostęp do `/dev/gpiochip0`, wybór układu i linii oraz okablowanie. Stan brokera nie jest informacją zwrotną z wyjścia. |
| Światło lub dzwonek wyłącza się po około minucie | To timeout klienta GPIO; trwająca rozmowa nie przedłuża go okresowo. |
| Wyjście włącza się po restarcie klienta GPIO | Sprawdź, czy broker zachował retained `on`; subskrypcja może ponownie zastosować tę komendę. |
| systemd zgłasza błąd zależności | Porównaj zależność szablonów od `wg-quick@wg0.service` z konfiguracją sieci. |
| Usługa działa w terminalu, ale nie przy starcie systemu | Sprawdź `User`, katalog domowy, właściciela pliku danych logowania, dostęp do urządzeń, ścieżki i architekturę programu oraz bibliotek. |

## Logi

Odczytaj logi usług Raspberry Pi:

```bash
journalctl -u sip-receiver.service -u mqtt-gpio.service -n 100 --no-pager
```

Jeśli konto nie może czytać dziennika, uruchom polecenie z `sudo`.
W przypadku Androida podłącz telefon z włączonym debugowaniem USB i użyj
Logcat w Android Studio albo uruchom:

```bash
adb logcat -d -s 'SIPClientDebug:V' '*:S'
```

Logi startowe Androida zawierają hasła MQTT i SIP. Usuń je przed udostępnieniem
wyniku. Klient GPIO zapisuje połączenia i odebrane komendy, a odbiornik SIP —
listy urządzeń, wybrane urządzenia, rejestrację i zdarzenia rozmów.

## Kontrole automatyczne

Po przygotowaniu środowiska kompilacji Androida uruchom z korzenia repozytorium:

```bash
cd SIPClient
bash ./gradlew :app:testDebugUnitTest
bash ./gradlew :app:connectedDebugAndroidTest
```

Ostatnie polecenie wymaga podłączonego urządzenia Android lub emulatora.
Istniejące testy sprawdzają tylko `2 + 2` i nazwę pakietu aplikacji. Nie testują
SIP, MQTT, obrazu, zachowania ustawień po restarcie ani GPIO. Repozytorium
nie ma testów funkcjonalnych C ani wspólnego skryptu weryfikacji całego projektu.

## Sprawdzenie instalacji

Wykonaj te kroki na docelowych urządzeniach po [instalacji](DEPLOYMENT.pl.md).
Uruchamiają podłączone światło i dzwonek; obserwuj rzeczywiste wyjścia oraz
aplikację. Istniejące diody zasilania i aktywności Raspberry Pi pomagają
potwierdzić start płytki, ale nie sygnalizują gotowości SIP ani MQTT.

1. Potwierdź zielony stan obu kontrolek aplikacji i rejestrację każdego odbiornika Raspberry Pi.
2. Zadzwoń kolejno przez **Salon**, **Garaż** i **Kuchnia**. Sprawdź obraz
   i dźwięk w odpowiednich kierunkach: odbiornik tylko wysyła obraz, a dźwięk
   sprawdź w obie strony. Rozłącz się i potwierdź powrót przycisków połączenia.
3. Podczas rozmowy obróć telefon, powiększ i przesuń obraz, a następnie przywróć widok.
4. Przy rozmowie z garażem sprawdź automatyczne włączenie światła, oba
   przełączniki, jawne komendy wyłączenia i osobne wyłączenie każdego wyjścia
   po około 60 sekundach. Potwierdź, że rozłączenie wyłącza światło, a nie wyłącza od razu dzwonka.
5. Sprawdź ponowne połączenie MQTT podczas rozmowy z garażem i bez rozmowy.
   Sprawdź restart GPIO przy retained `on` i `off`; `on` powinno ponownie uruchomić wyjście.
6. Zmień dane kont, uruchom aplikację ponownie i potwierdź użycie zapisanych
   wartości. Potem przywróć docelowe ustawienia.
7. Uruchom ponownie skonfigurowane usługi i potwierdź użycie zapisanych danych
   bez pytania w terminalu. Sprawdź utratę i odzyskanie sieci, również przez VPN.

Przy zgłaszaniu problemu zapisz wersję aplikacji, systemu i bibliotek, urządzenia
oraz obserwacje. Udana kompilacja lub przejście testów szablonowych nie
potwierdzają, że instalacja spełnia powyższe warunki.
