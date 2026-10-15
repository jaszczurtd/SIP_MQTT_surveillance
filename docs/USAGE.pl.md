# Obsługa aplikacji

[README](../README.pl.md) · [English](USAGE.md) · [Instalacja](DEPLOYMENT.pl.md) · [Diagnostyka](TROUBLESHOOTING.pl.md)

Najpierw skonfiguruj konta i uprawnienia, a potem sprawdź kontrolki **SIP**
i **MQTT**. Zielona oznacza rejestrację SIP lub połączenie z MQTT, żółta —
łączenie, a czerwona — brak nawiązanego połączenia. Kontrolki nie potwierdzają
działania kamery ani wyjścia GPIO.

## Rozmowy i obraz

Naciśnij **Salon**, **Garaż** albo **Kuchnia**, aby połączyć się z odpowiednim
odbiornikiem. Aplikacja wymaga rejestracji SIP przed wywołaniem połączenia.
Odbiornik Raspberry Pi automatycznie odbiera, wysyła obraz ze swojej kamery
i włącza dźwięk rozmowy. Aplikacja wybiera głośnik telefonu do odtwarzania.
Przycisk **Rozłącz** kończy rozmowę.

Obróć telefon, aby oglądać obraz w pionie lub poziomie. Gestem szczypania
zmienisz powiększenie od 1× do 5×, przeciągnięciem przesuniesz obraz,
a podwójnym dotknięciem lub przyciskiem przywrócisz widok początkowy.
Aplikacja utrzymuje włączony ekran, gdy jej aktywność jest widoczna.

Ikona koła zębatego otwiera ustawienia MQTT i SIP. Format pól i potrzebę
restartu po zmianach MQTT opisuje [instrukcja instalacji](DEPLOYMENT.pl.md).

## Światło i dzwonek

Przełączniki **Światło** i **Dzwonek** pojawiają się przy rozmowie z kontem
garażu (domyślnie `pi3`). Wysyłają tekstowe komendy MQTT:

| Przełącznik | Temat | Treść | Offset linii GPIO |
|---|---|---|---|
| Światło | `gpio/17` | `on` / `off` | `17` |
| Dzwonek | `gpio/27` | `on` / `off` | `27` |

Android publikuje z QoS **2** i flagą retained. Klient GPIO subskrybuje z QoS
**0**, rozpoznaje `on` i `off` bez rozróżniania wielkości liter, a pozostałe
komendy ignoruje. Android zaznacza przełącznik dla `on`, a dla innych wiadomości
go odznacza, dlatego wysyłaj tylko opisane wartości.

Przy dostępnym MQTT aplikacja wysyła też automatyczne komendy światła:

| Zdarzenie | Komenda |
|---|---|
| Nawiązanie rozmowy z garażem | Światło `on` |
| Zakończenie, rozłączenie lub błąd rozmowy z garażem | Światło `off` |
| Połączenie lub ponowne połączenie MQTT bez bieżącej rozmowy | Światło `off` |
| Połączenie lub ponowne połączenie MQTT podczas rozmowy z garażem | Światło `on` |

Zakończenie rozmowy nie wysyła automatycznie `off` dla dzwonka. Sterują nim
przełącznik i timeout klienta GPIO. Aplikacja nie kolejkuje komend wydanych,
gdy Android nie ma połączenia z MQTT.

Każde odebrane `on` uruchamia lub odnawia osobny licznik **60 sekund** w kliencie
GPIO. Po upływie tego czasu klient ustawia wyjście w stan niski i publikuje
retained `off` z QoS **1**. Sprawdza licznik w pętli z jednosekundową przerwą,
więc wyłączenie następuje w przybliżeniu po wskazanym czasie. Trwająca rozmowa
nie odnawia okresowo licznika światła. Jawne `off` kasuje licznik i od razu
wyłącza wyjście. Timeout działa podczas pracy programu GPIO; nie jest
sprzętowym watchdogiem.

Broker zachowuje ostatnią komendę dla każdego tematu i dostarcza ją nowym
subskrybentom. Retained `on` może więc ponownie włączyć wyjście po restarcie
klienta GPIO lub ponownym połączeniu i uruchomić nowy licznik. Android ustawia
przełączniki według tych wiadomości, ale nie otrzymuje potwierdzenia fizycznego
stanu wyjścia. Przy połączeniu MQTT bez rozmowy automatyczne `off` światła
zastępuje też poprzednią komendę retained dla światła.

## Zachowanie przy zmianie sieci

Aplikacja sprawdza, czy aktywna sieć Androida zgłasza transport Wi-Fi lub
komórkowy. Nie sprawdza dostępu do Internetu ani osiągalności serwera SIP
i brokera. Działanie z VPN zależy od właściwości aktywnej sieci zgłaszanych
przez Androida. Kontrola sieci może się powieść mimo niedostępnych usług.

Callbacki dostępności sieci obecnie tylko zapisują zmiany w logu. MQTT włącza
automatyczne ponawianie połączenia w Paho, ale aplikacja nie obsługuje pełnego
powrotu do pracy po każdej zmianie sieci. Jeśli po odzyskaniu sieci przyciski
pozostają ukryte, uruchom aplikację ponownie i sprawdź obie kontrolki połączenia.
