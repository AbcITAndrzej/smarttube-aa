# SmartTube AA — krótka instrukcja

Instrukcja dotyczy wersji `32.04-mobile-p13-aa1.18`. Jest to eksperymentalny, nieoficjalny fork SmartTube przeznaczony głównie do muzyki, playlist YouTube, radia i odtwarzania audio offline.

## Pierwsze uruchomienie

1. Pobierz i zainstaluj [najnowszy uniwersalny APK](https://github.com/AbcITAndrzej/smarttube-aa/releases/latest/download/SmartTube-AA-latest.apk).
2. Zaloguj się do YouTube, jeśli chcesz korzystać ze swoich playlist, historii i polubień.
3. Ustawienia aplikacji są dostępne z dolnego paska ekranu głównego.

## Android Auto

1. Otwórz `Ustawienia → Android Auto` i wykonaj instrukcję wyświetlaną przez aplikację.
2. W Android Auto włącz tryb programisty, a następnie w jego ustawieniach programisty zaznacz `Nieznane źródła`.
3. Wróć do SmartTube, wybierz `Dodaj / sprawdź` i ponownie połącz telefon z samochodem.

W Android Auto dostępne są przede wszystkim:

- `Playlisty` — playlisty z zalogowanego konta YouTube;
- `Radio` — ulubione stacje i katalog zsynchronizowany w telefonie;
- `Offline` — lokalnie zapisane audio, jeśli sekcja została włączona w ustawieniach;
- `Automatyczne` i `Więcej` — historia, polubiona muzyka oraz pozostałe źródła.

Aplikacja zapamiętuje ostatni utwór i kolejkę. Interfejs Android Auto działa wyłącznie jako odtwarzacz audio — oglądanie filmów na ekranie samochodu nie jest obecnie obsługiwane.

Pełną procedurę uruchomienia opisuje [instrukcja Android Auto](ANDROID-AUTO.md).

## Radio

1. Otwórz `Ustawienia → Radio`.
2. Wybierz synchronizację katalogu stacji.
3. Otwórz stację, aby jej posłuchać, lub dodaj ją do ulubionych. Ulubione są zapisywane lokalnie na telefonie i pojawiają się również w Android Auto.

Katalog pochodzi z bezpłatnej usługi Radio Browser. Błąd HTTP 503 zwykle oznacza chwilowy problem serwera albo blokadę w używanej sieci — spróbuj ponownie lub użyj innego połączenia internetowego.

## Jak działa Offline

Wszystkie pobrania Offline zawierają tylko audio i są przechowywane w prywatnej pamięci aplikacji.

| Sekcja | Co zawiera | Jak ją utworzyć |
|---|---|---|
| `Ostatnio zapisane automatycznie` | Utwory przechwycone przez funkcję `Słucham i zapisuję` | Włącz funkcję i słuchaj utworu przez ustawiony czas. Domyślnie zapis działa tylko dla playlisty YouTube odtwarzanej w Android Auto. |
| `Playlisty offline` | Lokalną kopię audio całej wybranej playlisty | Otwórz playlistę w telefonie, poczekaj na jej wczytanie i naciśnij ikonę pobierania. |
| `Ulubione offline` | Pobrane utwory, które są jednocześnie oznaczone jako polubione | Polub utwór i zapisz jego audio jedną z powyższych metod albo włącz zapas polubionej muzyki na podróż. |

Najważniejsze ustawienia znajdziesz w `Ustawienia → Offline`:

- `Słucham i zapisuję` — automatyczny zapis po określonym czasie słuchania;
- tryb domyślny i zalecany: `Tylko playlista w Android Auto`;
- `Tylko Wi-Fi / Ethernet` — po włączeniu pobieranie nie ruszy przez dane komórkowe;
- `Zarządzaj playlistami offline` — odtwarzanie, wstrzymanie, ponowienie lub usunięcie pobranej playlisty;
- `Inteligentny zapas na podróż` — przygotowanie ograniczonej liczby ostatnich, polubionych lub ostatnio używanych utworów;
- limit pamięci i automatyczne czyszczenie najstarszych danych.

Radio, transmisje na żywo, Shorts i materiały już odtwarzane offline nie są automatycznie zapisywane. Pobrana playlista jest lokalnym stanem z chwili pobrania — po zmianie playlisty na YouTube najpewniej trzeba ją usunąć i pobrać ponownie.

Aby korzystać z plików w samochodzie, włącz `Ustawienia → Offline → Pokaż bibliotekę Offline w Android Auto`. Możesz też włączyć automatyczne przejście na gotową lokalną kopię po utracie internetu.

## Przydatne informacje

- `Ustawienia → Diagnostyka` pokazują ostatnie zdarzenia i ułatwiają przygotowanie raportu błędu.
- Napisy, dubbing i jakość zależą od wariantów udostępnionych przez YouTube dla konkretnego materiału.
- Projekt jest nadal w budowie; przed aktualizacją warto zachować działający APK.
