# START HERE – SmartTube AA/mobile 2026-08-08

To pełne drzewo projektu oparte na paczce `smarttube-aa-fixed-2026-08-08`, z wdrożonym zaakceptowanym planem nowych funkcji oraz późniejszą warstwą SponsorBlock/DeArrow dla nowego mobile UI.

## Co zostało wdrożone

- **Ustawienia → Odtwarzacz** – osobne ustawienia tylko dla mobilnego playera; nie są czytane przez stabilny Android Auto.
- **Domyślny lektor / napisy** – preferowany język jest wyróżniany i lista przewija się do niego, ale nic nie jest automatycznie przełączane przy starcie filmu.
- **Nowy Material Bottom Sheet** dla jakości, audio i napisów.
- **Radio** – wyszukiwarka, istniejące wspólne Ulubione oraz eksperymentalny rolling time-shift 1/3/5 min dla progresywnych strumieni.
- **Android Auto Radio DVR** – `ACTION_SEEK_TO` obsługuje okno bufora bez naruszania logiki VOD.
- **Eksperymentalne AA Video** – osobny, domyślnie wyłączony komponent `CAR_LAUNCHER`; nie zmienia stabilnego `SmartTubeAutoMusicService` i nie podszywa się pod grę/nawigację.
- Zachowane są wcześniejsze poprawki z `FIXES_2026-08-08.md`.
- **SponsorBlock/DeArrow mobile enhancements** – brakujące znaczniki seekbara, DeArrow/original titles/fallback thumbnail na natywnych listach, cache + kontrolowana równoległość oraz naprawa konfliktu flag procesorów. Wszystkie cztery nowe mobilne bramki są domyślnie ON i można je wyłączyć. Stable Android Auto pozostaje odizolowane.

## Najpierw przeczytaj

1. `ENHANCEMENTS_2026-08-08.md` – najnowsza warstwa SponsorBlock/DeArrow dla nowego mobile UI, przełączniki, cache i checklista testowa.
2. `ENHANCEMENTS_VALIDATION_2026-08-08.md` – walidacja najnowszej warstwy.
3. `FEATURES_INTEGRATION_2026-08-08.md` – szczegółowy opis wcześniejszej architektury, funkcji i testów manualnych.
4. `VALIDATION_2026-08-08.md` – walidacja wcześniejszej paczki funkcjonalnej.
5. `AABROWSER_CLEAN_ROOM_NOTES.md` – granice implementacji eksperymentalnego AA Video.
6. `FIXES_2026-08-08.md` – opis wcześniejszych 6 poprawek projektu.

## Kompilacja

Dla wariantu mobilnego:

```bash
./gradlew :smarttubetv:testStmobileDebugUnitTest
./gradlew :smarttubetv:assembleStmobileDebug
```

W tej sesji pełny build nie był możliwy, ponieważ środowisko nie ma Android SDK ani lokalnego Gradle 7.5, a wrapper nie może pobrać dystrybucji z internetu. Statyczne kontrole i test aplikacji patcha są opisane w `VALIDATION_2026-08-08.md`.

## Patch

W paczce znajduje się również `FEATURES_2026-08-08.patch`. Jest on przeznaczony dla dokładnie tej samej bazowej wersji projektu. Najbezpieczniejsza integracja to jednak użycie pełnego drzewa źródłowego z ZIP-a.
