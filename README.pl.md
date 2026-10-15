# SIPClient — wideodomofon i sterowanie przez MQTT

[English](README.md)

SIPClient łączy telefon z Androidem z kamerami Raspberry Pi przez SIP
i steruje światłem oraz dzwonkiem przez MQTT. Aplikacja ma trzy przyciski
połączenia: **Salon**, **Garaż** i **Kuchnia**. Obraz można oglądać w pionie
i poziomie, powiększać gestem, przesuwać oraz przywracać przyciskiem.

Repozytorium zawiera [aplikację Android](SIPClient),
[odbiornik SIP](RaspberryPi/sip), który automatycznie odbiera połączenia,
oraz [klienta MQTT sterującego GPIO](RaspberryPi/mqtt). Serwer SIP z kontami
i broker MQTT wymagają osobnego przygotowania. Elementy systemu mogą łączyć
się przez sieć lokalną lub VPN; dostarczona konfiguracja Raspberry Pi używa
adresów sieci WireGuard.

Przełączniki światła i dzwonka pojawiają się podczas rozmowy z garażem.
Aplikacja steruje też światłem automatycznie, a klient GPIO wyłącza każde
wyjście po około 60 sekundach. Przed podłączeniem wyjść przeczytaj
[opis obsługi](docs/USAGE.pl.md).

## Uruchomienie systemu

1. [Skompiluj aplikację i programy Raspberry Pi](docs/BUILDING.pl.md).
   Przygotuj również certyfikat CA brokera wymagany przez build Androida.
2. [Skonfiguruj konta, urządzenia, sieć i usługi](docs/DEPLOYMENT.pl.md).
3. Przeczytaj [opis obsługi](docs/USAGE.pl.md) oraz
   [instrukcję diagnostyki i sprawdzania działania](docs/TROUBLESHOOTING.pl.md).

Aplikacja Android korzysta z Linphone SDK i Eclipse Paho. Programy Raspberry Pi
używają liblinphone, libmosquitto i libgpiod. Wymagania kompilacji oraz wersje
zależności znajdziesz w instrukcji kompilacji.

## Licencja

MIT. Zależności mają własne licencje.
