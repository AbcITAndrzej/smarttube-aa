# SmartTube AA

[![Build SmartTube AA Music APK](https://github.com/AbcITAndrzej/smarttube-aa/actions/workflows/build-smarttube-aa.yml/badge.svg?branch=main)](https://github.com/AbcITAndrzej/smarttube-aa/actions/workflows/build-smarttube-aa.yml)
[![Build SmartTube AA Video EXP APK](https://github.com/AbcITAndrzej/smarttube-aa/actions/workflows/build-smarttube-aa-video-exp.yml/badge.svg?branch=main)](https://github.com/AbcITAndrzej/smarttube-aa/actions/workflows/build-smarttube-aa-video-exp.yml)

SmartTube AA to eksperymentalny fork SmartTube przygotowany do wygodnej obsługi YouTube na telefonie oraz muzyki, playlist i radia internetowego w Android Auto.

Projekt nie jest oficjalną aplikacją Google ani YouTube. Jest rozwijany do testów i celów badawczych.

## Aktualna wersja produkcyjna

Aktualnie jako **wersję produkcyjną** projektu wskazujemy build `aa1.41-lockscreen-test1` dla architektury **ARM64-v8a**. Oba warianty zostały zbudowane z tej samej bazy kodu i mogą być zainstalowane jednocześnie, ponieważ mają różne identyfikatory pakietów.

### SmartTube AA Music — produkcyjny

Pakiet: `app.smarttube.mobile`

Gałąź produkcyjna: [`production/music`](https://github.com/AbcITAndrzej/smarttube-aa/tree/production/music)

**[⬇ Pobierz aktualny produkcyjny APK — SmartTube AA Music aa1.41](https://github.com/AbcITAndrzej/smarttube-aa/releases/download/aa1.41-lockscreen-test1/SmartTube-AA-Music-aa1.41-lockscreen-test1-arm64-v8a.apk)**

Music jest podstawowym wariantem projektu. Obsługuje muzykę, playlisty i radio w Android Auto przez `SmartTubeAutoMusicService`.

### SmartTube AA Video EXP — produkcyjny wariant Video

Pakiet: `app.smarttube.mobile.carvideo`

Gałąź produkcyjna: [`production/video-exp`](https://github.com/AbcITAndrzej/smarttube-aa/tree/production/video-exp)

**[⬇ Pobierz aktualny produkcyjny APK — SmartTube AA Video EXP aa1.41](https://github.com/AbcITAndrzej/smarttube-aa/releases/download/aa1.41-lockscreen-test1/SmartTube-AA-Video-EXP-aa1.41-lockscreen-test1-arm64-v8a.apk)**

Video EXP jest osobnym wariantem przeznaczonym do eksperymentalnego wyświetlania interfejsu i obrazu na ekranie Android Auto podczas postoju. Może być zainstalowany jednocześnie z Music.

Pełna strona wydania: [`aa1.41-lockscreen-test1`](https://github.com/AbcITAndrzej/smarttube-aa/releases/tag/aa1.41-lockscreen-test1).

> Nazwa pliku i tagu nadal zawiera `lockscreen-test1`, ponieważ dokładnie ten sprawdzony build został wskazany jako obecna wersja produkcyjna. Linki powyżej prowadzą bezpośrednio do aktualnych plików `.apk` — nie trzeba pobierać ani rozpakowywać ZIP-a.

## Stan projektu

| Wariant | Pakiet | Gałąź produkcyjna | Aktualny APK |
| --- | --- | --- | --- |
| **SmartTube AA Music** | `app.smarttube.mobile` | `production/music` | **aa1.41-lockscreen-test1 ARM64-v8a** |
| **SmartTube AA Video EXP** | `app.smarttube.mobile.carvideo` | `production/video-exp` | **aa1.41-lockscreen-test1 ARM64-v8a** |

Obie gałęzie produkcyjne wskazują ten sam commit źródłowy `368c6db1f39577fe6b7ba1b1235e8571539ade1f`, z którego powstały powyższe dwa APK. To jest **jedno repozytorium i jedna wspólna baza kodu**, z której powstają dwa osobno instalowane warianty. Szczegóły: [Music i Video EXP](docs/MUSIC-AND-VIDEO.md).

## Co zawiera obecny build produkcyjny

Wspólne dla Music i Video EXP:

- poprawka crasha Shorts po dojściu do końca filmu: SABR prawidłowo zgłasza `endOfStream` zamiast ponownie pobierać ostatni segment aż do `OutOfMemoryError`,
- poprawione otwieranie playlist i linków sekcji w mobilnym interfejsie,
- polski lektor i multi-audio,
- obsługa `audioTrackId`, `isAutoDubbed` i `xtags`,
- poprawiony pipeline SABR z osobnymi torami audio/video,
- kompletna poprawka cross-track SABR,
- napisy,
- logowanie do konta YouTube,
- diagnostyka i recovery odtwarzania,
- jeden spójny i przewijany ekran **Ustawień** w telefonie i układzie poziomym,
- **KONTA** jako pierwsza pozycja Ustawień,
- następnie ustawienia mobile/Android Auto,
- niżej pozostałe oryginalne kategorie SmartTube w tym samym stylu,
- sterowanie z ekranu blokady: poprzedni / play-pause / następny oraz metadane i grafika utworu/filmu.

Video EXP korzysta z tej samej warstwy `audioTrack.id` / dubbing / SABR co Music i zawiera tę samą poprawkę Shorts.

## Najważniejsze funkcje

- logowanie do konta YouTube,
- muzyka i playlisty YouTube w Android Auto,
- zapamiętywanie ostatniego utworu, playlisty i kolejki,
- radio internetowe z katalogiem stacji i ulubionymi,
- mobilna strona główna, wyszukiwanie, Shorts i player,
- player mobilny z poprzednim/następnym filmem, gestami przewijania, zoomem 1–4×, PiP i pełnym ekranem,
- szybki wybór napisów, ścieżki audio/lektora, jakości i prędkości,
- osobno instalowany wariant Video EXP przeznaczony do testów obrazu na postoju.

## Buildy rozwojowe z `main`

Gałąź `main` pozostaje miejscem dalszego rozwoju. Każda zmiana na `main` może uruchamiać osobne workflowy:

- **[Build SmartTube AA Music APK](https://github.com/AbcITAndrzej/smarttube-aa/actions/workflows/build-smarttube-aa.yml)** → aktualizuje `latest-main`,
- **[Build SmartTube AA Video EXP APK](https://github.com/AbcITAndrzej/smarttube-aa/actions/workflows/build-smarttube-aa-video-exp.yml)** → aktualizuje `latest-video-exp`.

Te automatyczne wydania z `main` są buildami rozwojowymi. **Do zwykłej instalacji zalecane są pliki wskazane wyżej w sekcji „Aktualna wersja produkcyjna”.**

## Jak uruchomić w Android Auto

1. Pobierz odpowiedni plik APK z sekcji **Aktualna wersja produkcyjna**.
2. Zainstaluj APK ręcznie na telefonie.
3. W SmartTube otwórz `Ustawienia → Android Auto` i wykonaj instrukcję na ekranie.
4. Włącz tryb programisty oraz opcję „Nieznane źródła” w ustawieniach Android Auto.
5. Wróć do SmartTube i wybierz „Dodaj / sprawdź”.
6. Podłącz ponownie telefon do samochodu i uruchom SmartTube AA.

Szczegóły i ograniczenia opisuje [instrukcja Android Auto](docs/ANDROID-AUTO.md). Dostępna jest również [krótka instrukcja użytkownika](docs/INSTRUKCJA-UZYTKOWNIKA.md).

## Ważne informacje

Automatyczne tłumaczenia, dubbing i polski lektor zależą od ścieżek udostępnionych przez YouTube dla danego filmu.

Interfejs multimedialny Android Auto w wariancie Music pozostaje audio-only. Video EXP jest eksperymentalnym wariantem przeznaczonym do testów obrazu na postoju; system samochodu może ograniczyć albo zablokować obraz zgodnie ze swoją polityką bezpieczeństwa.

## Pochodzenie

Projekt bazuje na otwartym kodzie SmartTube i zachowuje oryginalne informacje o licencji oraz autorach.
