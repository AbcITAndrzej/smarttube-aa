# SmartTube AA

[![Build SmartTube AA APK](https://github.com/AbcITAndrzej/smarttube-aa/actions/workflows/build-smarttube-aa.yml/badge.svg?branch=main)](https://github.com/AbcITAndrzej/smarttube-aa/actions/workflows/build-smarttube-aa.yml)

SmartTube AA to eksperymentalny fork SmartTube przygotowany do wygodnej obsługi YouTube na telefonie oraz muzyki, playlist i radia internetowego w Android Auto.

Projekt nie jest oficjalną aplikacją Google ani YouTube. Jest rozwijany do testów i celów badawczych.

## Stan projektu

**To jest jedno repozytorium zawierające dwa osobno instalowane warianty aplikacji — nie dwa osobne projekty GitHub.**

| Wariant | Pakiet | Stan |
| --- | --- | --- |
| **SmartTube AA Music** | `app.smarttube.mobile` | **aktualnie rozwijany i gotowy do testów z `main` — aa1.39-test14** |
| **SmartTube AA Video EXP** | `app.smarttube.mobile.carvideo` | kod jest w repo; eksperymentalny wariant nadal istnieje, ale aktualny workflow aa1.39 z `main` nie publikuje jeszcze jego nowego APK |

Obie aplikacje mogą być zainstalowane jednocześnie. Szczegóły: [Music i Video EXP](docs/MUSIC-AND-VIDEO.md).

## Pobieranie — aktualny build z `main`

Aktualnie zweryfikowany build **SmartTube AA Music aa1.39-test14**:

- **[Build #49 — GitHub Actions](https://github.com/AbcITAndrzej/smarttube-aa/actions/runs/33733156005)** — zakończony poprawnie,
- **[UNIVERSAL](https://github.com/AbcITAndrzej/smarttube-aa/actions/runs/33733156005/artifacts/9884843094)** — zalecany dla większości użytkowników,
- **[ARM64-v8a](https://github.com/AbcITAndrzej/smarttube-aa/actions/runs/33733156005/artifacts/9884841631)**,
- **[ARMv7 / armeabi-v7a](https://github.com/AbcITAndrzej/smarttube-aa/actions/runs/33733156005/artifacts/9884844164)**.

Artefakty GitHub Actions są pobierane jako ZIP zawierający plik APK.

Najnowsze kolejne buildy z `main` są zawsze widoczne tutaj:

**[GitHub Actions — Build SmartTube AA APK](https://github.com/AbcITAndrzej/smarttube-aa/actions/workflows/build-smarttube-aa.yml)**

### Video EXP

Kod **SmartTube AA Video EXP** nadal znajduje się w tym samym repozytorium i korzysta z wariantu Gradle `StmobileCarvideo`. Ostatni opublikowany Release zawierający gotowe APK Video EXP to **aa1.23**:

- **[SmartTube AA Video EXP — ostatni Release APK](https://github.com/AbcITAndrzej/smarttube-aa/releases/latest/download/SmartTube-AA-Video-EXP-latest.apk)**
- **[GitHub Releases](https://github.com/AbcITAndrzej/smarttube-aa/releases/latest)**

Nie należy mylić starego Release aa1.23 z aktualnym buildem Music aa1.39-test14 z GitHub Actions.

## Co zawiera aa1.39-test14

Wersja aa1.39-test14 przenosi kompletną poprawkę upstream dotyczącą inicjalizacji cross-track SABR, zachowując wcześniejsze zmiany projektu:

- polski lektor i multi-audio,
- obsługę `audioTrackId` oraz `xtags`,
- napisy,
- logowanie diagnostyczne,
- logowanie do konta YouTube,
- poprawki odzyskiwania odtwarzania bez degradacji prawdziwego katalogu multi-audio,
- poprawkę błędu SABR, który wcześniej potrafił zakończyć odtwarzanie crashem,
- automatyczny build APK po zmianach na gałęzi `main`.

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

1. Zainstaluj odpowiedni APK ręcznie na telefonie.
2. W SmartTube otwórz `Ustawienia → Android Auto` i wykonaj instrukcję na ekranie.
3. Włącz tryb programisty oraz opcję „Nieznane źródła” w ustawieniach Android Auto.
4. Wróć do SmartTube i wybierz „Dodaj / sprawdź”.
5. Podłącz ponownie telefon do samochodu i uruchom SmartTube AA.

Szczegóły i ograniczenia opisuje [instrukcja Android Auto](docs/ANDROID-AUTO.md). Dostępna jest również [krótka instrukcja użytkownika](docs/INSTRUKCJA-UZYTKOWNIKA.md).

## Ważne informacje

Automatyczne tłumaczenia i dubbing zależą od ścieżek udostępnionych przez YouTube dla danego filmu.

Interfejs multimedialny Android Auto pozostaje audio-only. Video EXP jest wariantem eksperymentalnym przeznaczonym do testów na postoju; system samochodu może ograniczyć albo zablokować obraz zgodnie ze swoją polityką bezpieczeństwa.

## Pochodzenie

Projekt bazuje na otwartym kodzie SmartTube i zachowuje oryginalne informacje o licencji oraz autorach.
