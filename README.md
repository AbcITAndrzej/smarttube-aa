# SmartTube AA

[![Build SmartTube AA APK](https://github.com/AbcITAndrzej/smarttube-aa/actions/workflows/build-smarttube-aa.yml/badge.svg?branch=main)](https://github.com/AbcITAndrzej/smarttube-aa/actions/workflows/build-smarttube-aa.yml)

SmartTube AA to eksperymentalny fork SmartTube przygotowany do wygodnej obsługi YouTube na telefonie oraz muzyki, playlist i radia internetowego w Android Auto.

Projekt nie jest oficjalną aplikacją Google ani YouTube. Jest rozwijany do testów i celów badawczych.

## Pobierz APK bez ZIP-a

Aktualny publiczny wariant **SmartTube AA Music aa1.39-test14** jest publikowany bezpośrednio jako pliki `.apk` w stałym wydaniu `latest-main`.

### Zalecane

**[⬇ Pobierz SmartTube AA Music — UNIVERSAL APK](https://github.com/AbcITAndrzej/smarttube-aa/releases/download/latest-main/SmartTube-AA-Music-universal.apk)**

Dla większości telefonów wybierz wersję **UNIVERSAL**.

### Pozostałe architektury

- **[⬇ ARM64-v8a APK](https://github.com/AbcITAndrzej/smarttube-aa/releases/download/latest-main/SmartTube-AA-Music-arm64-v8a.apk)** — większość nowszych telefonów,
- **[⬇ ARMv7 / armeabi-v7a APK](https://github.com/AbcITAndrzej/smarttube-aa/releases/download/latest-main/SmartTube-AA-Music-armeabi-v7a.apk)** — starsze urządzenia,
- **[SHA256SUMS.txt](https://github.com/AbcITAndrzej/smarttube-aa/releases/download/latest-main/SHA256SUMS.txt)** — sumy kontrolne plików.

**[Otwórz stronę wydania latest-main](https://github.com/AbcITAndrzej/smarttube-aa/releases/tag/latest-main)**

Pliki powyżej są udostępniane jako zwykłe APK z GitHub Releases — użytkownik nie musi pobierać ani rozpakowywać ZIP-a. GitHub Actions nadal przechowuje techniczne archiwa buildów, ale do normalnej instalacji należy używać linków APK powyżej.

## Stan projektu

**To jest jedno repozytorium zawierające dwa osobno instalowane warianty aplikacji — nie dwa osobne projekty GitHub.**

| Wariant | Pakiet | Stan |
| --- | --- | --- |
| **SmartTube AA Music** | `app.smarttube.mobile` | **aktualnie rozwijany i publikowany z `main` — aa1.39-test14** |
| **SmartTube AA Video EXP** | `app.smarttube.mobile.carvideo` | kod jest w repo; eksperymentalny wariant nadal istnieje, ale workflow aa1.39 nie publikuje jeszcze jego nowego APK |

Obie aplikacje mogą być zainstalowane jednocześnie. Szczegóły: [Music i Video EXP](docs/MUSIC-AND-VIDEO.md).

### Video EXP

Kod **SmartTube AA Video EXP** nadal znajduje się w tym samym repozytorium i korzysta z wariantu Gradle `StmobileCarvideo`. Ostatni opublikowany Release zawierający gotowe APK Video EXP to **aa1.23**:

- **[⬇ SmartTube AA Video EXP aa1.23 — APK](https://github.com/AbcITAndrzej/smarttube-aa/releases/download/v32.04-mobile-p13-aa1.23/SmartTube-AA-Video-EXP-latest.apk)**
- **[Release aa1.23](https://github.com/AbcITAndrzej/smarttube-aa/releases/tag/v32.04-mobile-p13-aa1.23)**

Nie należy mylić starego Video EXP aa1.23 z aktualnym buildem Music aa1.39-test14.

## Automatyczne publikowanie z `main`

Każda zmiana na gałęzi `main` uruchamia workflow **Build SmartTube AA APK**. Po poprawnym buildzie aktualizowane są stałe pliki w wydaniu `latest-main`, więc powyższe linki nie zmieniają się przy kolejnych buildach.

**[GitHub Actions — Build SmartTube AA APK](https://github.com/AbcITAndrzej/smarttube-aa/actions/workflows/build-smarttube-aa.yml)**

## Co zawiera aa1.39-test14

Wersja aa1.39-test14 przenosi kompletną poprawkę upstream dotyczącą inicjalizacji cross-track SABR, zachowując wcześniejsze zmiany projektu:

- polski lektor i multi-audio,
- obsługę `audioTrackId` oraz `xtags`,
- napisy,
- logowanie diagnostyczne,
- logowanie do konta YouTube,
- poprawki odzyskiwania odtwarzania bez degradacji prawdziwego katalogu multi-audio,
- poprawkę błędu SABR, który wcześniej potrafił zakończyć odtwarzanie crashem,
- automatyczny build i publikowanie bezpośrednich APK po zmianach na gałęzi `main`.

## Najważniejsze funkcje

- logowanie do konta YouTube,
- muzyka i playlisty YouTube w Android Auto,
- zapamiętywanie ostatniego utworu, playlisty i kolejki,
- radio internetowe z katalogiem stacji i ulubionymi,
- mobilna strona główna, wyszukiwanie, Shorts i player,
- player mobilny z poprzednim/następnym filmem, gestami przewijania, zoomem 1–4×, PiP i pełnym ekranem,
- szybki wybór napisów, ścieżki audio/lektora, jakości i prędkości,
- eksperymentalny, osobno instalowany wariant Video EXP przeznaczony do testów obrazu na postoju.

## Jak uruchomić w Android Auto

1. Pobierz odpowiedni plik APK z sekcji **Pobierz APK bez ZIP-a**.
2. Zainstaluj APK ręcznie na telefonie.
3. W SmartTube otwórz `Ustawienia → Android Auto` i wykonaj instrukcję na ekranie.
4. Włącz tryb programisty oraz opcję „Nieznane źródła” w ustawieniach Android Auto.
5. Wróć do SmartTube i wybierz „Dodaj / sprawdź”.
6. Podłącz ponownie telefon do samochodu i uruchom SmartTube AA.

Szczegóły i ograniczenia opisuje [instrukcja Android Auto](docs/ANDROID-AUTO.md). Dostępna jest również [krótka instrukcja użytkownika](docs/INSTRUKCJA-UZYTKOWNIKA.md).

## Ważne informacje

Automatyczne tłumaczenia i dubbing zależą od ścieżek udostępnionych przez YouTube dla danego filmu.

Interfejs multimedialny Android Auto pozostaje audio-only. Video EXP jest wariantem eksperymentalnym przeznaczonym do testów na postoju; system samochodu może ograniczyć albo zablokować obraz zgodnie ze swoją polityką bezpieczeństwa.

## Pochodzenie

Projekt bazuje na otwartym kodzie SmartTube i zachowuje oryginalne informacje o licencji oraz autorach.
