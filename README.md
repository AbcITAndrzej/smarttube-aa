# SmartTube AA

SmartTube AA to eksperymentalny fork SmartTube przygotowany do muzyki, playlist YouTube i radia internetowego w Android Auto oraz do wygodnej obsługi na telefonie.

Projekt nie jest oficjalną aplikacją Google ani YouTube. Jest rozwijany do testów i celów badawczych.

## Pobieranie

**[Pobierz zawsze najnowszy uniwersalny APK](https://github.com/AbcITAndrzej/smarttube-aa/releases/latest/download/SmartTube-AA-latest.apk)**

Aktualne wydanie: `32.04-mobile-p13-aa1.18`

Pozostałe warianty APK i archiwum źródeł są dostępne w [GitHub Releases](https://github.com/AbcITAndrzej/smarttube-aa/releases/latest).

**[Krótka instrukcja korzystania z aplikacji](docs/INSTRUKCJA-UZYTKOWNIKA.md)** — Android Auto, Radio, playlisty i tryb Offline.

## Najważniejsze funkcje

- logowanie do konta YouTube,
- muzyka i playlisty YouTube w Android Auto,
- zapamiętywanie ostatniego utworu, playlisty i kolejki,
- radio internetowe z katalogiem stacji i ulubionymi,
- mobilna strona główna, wyszukiwanie, Shorts i player,
- player mobilny z poprzednim/następnym filmem, gestami przewijania, zoomem 1–4×, PiP i pełnym ekranem,
- szybki wybór napisów, ścieżki audio/lektora, jakości i prędkości.

## Jak uruchomić w Android Auto

1. Zainstaluj APK ręcznie na telefonie.
2. W SmartTube otwórz `Ustawienia → Android Auto` i wykonaj instrukcję na ekranie.
3. Włącz tryb programisty oraz opcję „Nieznane źródła” w ustawieniach Android Auto.
4. Wróć do SmartTube i wybierz „Dodaj / sprawdź”.
5. Podłącz ponownie telefon do samochodu i uruchom SmartTube AA.

Szczegóły i ograniczenia opisuje [instrukcja Android Auto](docs/ANDROID-AUTO.md).

## Co zmienia aa1.18

- dodaje bezpieczny domyślny zapis offline wyłącznie dla utworów z aktywnej playlisty odtwarzanej przez Android Auto,
- zapisuje wyłącznie strumień audio; radio, transmisje live, Shorts i materiały już offline pozostają wykluczone,
- pozwala wybrać szerszy tryb zapisu: całe AA, wszystkie playlisty albo całe zwykłe odtwarzanie YouTube.

## Zmiany z aa1.17

- integruje etapy 1–12 przebudowy mobilnej, diagnostyki, wydajności i pracy offline,
- dodaje fundament zapisu audio offline, jawne pobieranie playlist i rezerwę na podróż,
- przygotowuje osobny, eksperymentalny wariant AA Video bez usługi muzycznej Android Auto,
- poprawia wybór ścieżek w poziomie, preferowany język, kolejkę zwykłych filmów i obrót playera,
- wcześniej doładowuje Shorts i pokazuje stan oczekiwania na kolejną stronę.

## Zmiany z aa1.16

- preferowany język audio jest ponownie sprawdzany po załadowaniu ścieżek; polska ścieżka zostaje wybrana, gdy YouTube ją udostępnia,
- Shorts korzystają z własnej kolejki, dlatego gest góra/dół nie przełącza już na zwykły film,
- ekran Shorts oraz pozostałe obsługiwane feedy doładowują następne strony przy przewijaniu,
- sekcja „Wszystkie” nie miesza już kafelków Shorts ze zwykłymi filmami,
- po powiększeniu filmu można przesuwać obraz jednym palcem w granicach jego krawędzi,
- ponowienie odtwarzania po przejściowym błędzie strumienia 403 rozpoczyna się szybciej.

Automatyczne tłumaczenia i dubbing zależą od ścieżek udostępnionych przez YouTube dla danego filmu. Interfejs multimedialny Android Auto pozostaje audio-only — eksperymentalne ustawienie nie omija blokad obrazu na ekranie samochodu. Funkcje Android Auto i radio pozostają eksperymentalne.

## Pochodzenie

Projekt bazuje na otwartym kodzie SmartTube i zachowuje oryginalne informacje o licencji oraz autorach.
