# SmartTube AA

SmartTube AA to eksperymentalny fork SmartTube przygotowany do muzyki, playlist YouTube i radia internetowego w Android Auto oraz do wygodnej obsługi na telefonie.

Projekt nie jest oficjalną aplikacją Google ani YouTube. Jest rozwijany do testów i celów badawczych.

## Pobieranie

**[Pobierz zawsze najnowszy uniwersalny APK](https://github.com/AbcITAndrzej/smarttube-aa/releases/latest/download/SmartTube-AA-latest.apk)**

Aktualne wydanie: `32.04-mobile-p13-aa1.15`

Pozostałe warianty APK i archiwum źródeł są dostępne w [GitHub Releases](https://github.com/AbcITAndrzej/smarttube-aa/releases/latest).

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

## Co zmienia aa1.15

- naprawiono wiszące „Proszę czekać, trwa ładowanie danych” przy następnym filmie,
- włączono pełne odświeżanie danych odtwarzania po przejściowych błędach źródła, w tym 403,
- środkowe przyciski ±10 s zastąpiono poprzednim i następnym filmem; przewijanie nadal działa gestem i paskiem,
- dodano powiększanie filmu dwoma palcami w zwykłym i pełnoekranowym playerze,
- poprawiono PiP oraz powrót z pełnego ekranu do pionu,
- rozdzielono identyfikatory tłumaczonych napisów, aby kliknięty język nie wskazywał innej ścieżki.

Automatyczne tłumaczenia, w tym polskie, zależą od ścieżek udostępnionych przez YouTube dla danego filmu. Funkcje Android Auto i radio pozostają eksperymentalne.

## Pochodzenie

Projekt bazuje na otwartym kodzie SmartTube i zachowuje oryginalne informacje o licencji oraz autorach.
