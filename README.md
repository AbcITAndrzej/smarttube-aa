# SmartTube AA

[![Build SmartTube AA Music APK](https://github.com/AbcITAndrzej/smarttube-aa/actions/workflows/build-smarttube-aa.yml/badge.svg?branch=main)](https://github.com/AbcITAndrzej/smarttube-aa/actions/workflows/build-smarttube-aa.yml)
[![Build SmartTube AA Video EXP APK](https://github.com/AbcITAndrzej/smarttube-aa/actions/workflows/build-smarttube-aa-video-exp.yml/badge.svg?branch=main)](https://github.com/AbcITAndrzej/smarttube-aa/actions/workflows/build-smarttube-aa-video-exp.yml)

SmartTube AA to eksperymentalny fork SmartTube przygotowany do wygodnej obsługi YouTube na telefonie oraz muzyki, playlist i radia internetowego w Android Auto.

Projekt nie jest oficjalną aplikacją Google ani YouTube. Jest rozwijany do testów i celów badawczych.

## Pobierz APK bez ZIP-a

Repozytorium publikuje **dwa osobno instalowane warianty** z tej samej bazy kodu aa1.40.

### SmartTube AA Music — zalecany

Pakiet: `app.smarttube.mobile`

**[⬇ Pobierz SmartTube AA Music — UNIVERSAL APK](https://github.com/AbcITAndrzej/smarttube-aa/releases/download/latest-main/SmartTube-AA-Music-universal.apk)**

- **[ARM64-v8a APK](https://github.com/AbcITAndrzej/smarttube-aa/releases/download/latest-main/SmartTube-AA-Music-arm64-v8a.apk)** — większość nowszych telefonów,
- **[ARMv7 / armeabi-v7a APK](https://github.com/AbcITAndrzej/smarttube-aa/releases/download/latest-main/SmartTube-AA-Music-armeabi-v7a.apk)** — starsze urządzenia,
- **[Release Music `latest-main`](https://github.com/AbcITAndrzej/smarttube-aa/releases/tag/latest-main)**.

Music jest podstawowym wariantem projektu. Obsługuje muzykę, playlisty i radio w Android Auto przez `SmartTubeAutoMusicService`.

### SmartTube AA Video EXP — eksperymentalny

Pakiet: `app.smarttube.mobile.carvideo`

**[⬇ Pobierz SmartTube AA Video EXP — UNIVERSAL APK](https://github.com/AbcITAndrzej/smarttube-aa/releases/download/latest-video-exp/SmartTube-AA-Video-EXP-universal.apk)**

- **[ARM64-v8a APK](https://github.com/AbcITAndrzej/smarttube-aa/releases/download/latest-video-exp/SmartTube-AA-Video-EXP-arm64-v8a.apk)**,
- **[ARMv7 / armeabi-v7a APK](https://github.com/AbcITAndrzej/smarttube-aa/releases/download/latest-video-exp/SmartTube-AA-Video-EXP-armeabi-v7a.apk)**,
- **[Release Video EXP `latest-video-exp`](https://github.com/AbcITAndrzej/smarttube-aa/releases/tag/latest-video-exp)**.

Video EXP jest osobnym wariantem przeznaczonym do eksperymentalnego wyświetlania interfejsu i obrazu na ekranie Android Auto podczas postoju. Może być zainstalowany jednocześnie z Music.

Pliki są publikowane jako zwykłe `.apk` w GitHub Releases — użytkownik nie musi pobierać ani rozpakowywać ZIP-a. Stałe linki powyżej pozostają takie same przy kolejnych aktualizacjach.

## Stan projektu

| Wariant | Pakiet | Stan |
| --- | --- | --- |
| **SmartTube AA Music** | `app.smarttube.mobile` | **aa1.40 — aktywnie publikowany z `main`** |
| **SmartTube AA Video EXP** | `app.smarttube.mobile.carvideo` | **aa1.40 — aktywnie publikowany z `main` jako wariant eksperymentalny** |

To jest **jedno repozytorium i jedna wspólna baza kodu**, z której powstają dwa osobno instalowane APK. Szczegóły: [Music i Video EXP](docs/MUSIC-AND-VIDEO.md).

## Co zawiera aa1.40

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
- niżej pozostałe oryginalne kategorie SmartTube w tym samym stylu.

Video EXP aa1.40 korzysta z tej samej warstwy `audioTrack.id` / dubbing / SABR co Music i zawiera tę samą poprawkę Shorts.

## Najważniejsze funkcje

- logowanie do konta YouTube,
- muzyka i playlisty YouTube w Android Auto,
- zapamiętywanie ostatniego utworu, playlisty i kolejki,
- radio internetowe z katalogiem stacji i ulubionymi,
- mobilna strona główna, wyszukiwanie, Shorts i player,
- player mobilny z poprzednim/następnym filmem, gestami przewijania, zoomem 1–4×, PiP i pełnym ekranem,
- szybki wybór napisów, ścieżki audio/lektora, jakości i prędkości,
- eksperymentalny, osobno instalowany wariant Video EXP przeznaczony do testów obrazu na postoju.

## Automatyczne publikowanie z `main`

Każda zmiana na gałęzi `main` uruchamia osobne workflowy:

- **[Build SmartTube AA Music APK](https://github.com/AbcITAndrzej/smarttube-aa/actions/workflows/build-smarttube-aa.yml)** → aktualizuje `latest-main`,
- **[Build SmartTube AA Video EXP APK](https://github.com/AbcITAndrzej/smarttube-aa/actions/workflows/build-smarttube-aa-video-exp.yml)** → aktualizuje `latest-video-exp`.

Dzięki temu stałe bezpośrednie linki do APK nie zmieniają się przy kolejnych buildach i mogą być używane do ręcznej aktualizacji.

## Jak uruchomić w Android Auto

1. Pobierz odpowiedni plik APK z sekcji **Pobierz APK bez ZIP-a**.
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
