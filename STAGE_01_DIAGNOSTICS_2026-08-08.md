# Etap 01 — Diagnostyka + FeatureFlags

Data paczki: 2026-08-08
Baza: `smarttube-aa-enhancements-2026-08-08.zip`

## Cel etapu

Dodać bezpieczną warstwę diagnostyczną przed kolejnymi zmianami w playerze, paginacji, radiu i trybie offline. Diagnostyka jest lokalna: aplikacja niczego sama nie wysyła na zewnętrzny serwer. Raport trafia poza aplikację wyłącznie wtedy, gdy użytkownik ręcznie użyje przycisku „Kopiuj raport”.

## Co zostało dodane

### 1. Ustawienia → Diagnostyka

Nowy ekran zawiera:

- odświeżanie raportu,
- kopiowanie raportu do schowka,
- czyszczenie lokalnych liczników i bufora zdarzeń,
- przełącznik zbierania diagnostyki,
- przełącznik dołączania ostatnich zdarzeń do raportu.

### 2. Informacje w raporcie

Raport zawiera m.in.:

- wersję aplikacji, package id i wersję Androida,
- producenta/model urządzenia i główne ABI,
- właściciela aktualnego/ostatniego playbacku: MOBILE albo ANDROID_AUTO,
- stan playbacku,
- zamaskowany identyfikator filmu (pełny YouTube ID nie jest kopiowany),
- tytuł, pozycję, długość i buffered position,
- czas `prepare -> READY` oraz `prepare -> PLAYING`,
- aktywną ścieżkę video/audio/napisów,
- rozdzielczość, fps, kodek i bitrate tam, gdzie ExoPlayer udostępnia te dane,
- rodzaj źródła (DASH/HLS/SABR/url-list/radio) i sam host bez pełnego podpisanego URL,
- ostatni błąd,
- liczbę przejściowych 403 i potwierdzonych odzyskań,
- liczbę restartów/reload/retry,
- liczbę fallbacków Radio DVR,
- liczbę znaczników SponsorBlock/rozdziałów na osi czasu,
- ustawienia SponsorBlock i DeArrow,
- rozmiar cache metadanych DeArrow jako liczbę wpisów,
- ostatni czas pobierania metadanych DeArrow/original-title,
- stan Radio DVR, seekowalność, długość okna i aktualny rozmiar bufora w bajtach,
- stan eksperymentalnego AA parked-video.

### 3. FeatureFlags

Dodano `MobileFeatureFlags` jako centralną bramkę wdrożeniową dla kolejnych etapów.

Mechanizm ma dwa poziomy:

1. bezpieczna wartość domyślna zapisana w kodzie,
2. opcjonalny override zapisany lokalnie w SharedPreferences.

Dzięki temu kolejne funkcje można będzie wyłączyć bez usuwania lub cofania implementacji. Ustawienia użytkownika (np. player/radio) pozostają osobną warstwą i w kolejnych etapach będą łączone z FeatureFlagiem.

W etapie 1 aktywne flagi to:

- `diagnostics_capture` — domyślnie ON,
- `diagnostics_recent_events` — domyślnie ON.

### 4. Lokalny ring-buffer zdarzeń

`MobileDiagnostics` przechowuje do 200 ostatnich zdarzeń procesu. Raport dołącza maksymalnie 80 najnowszych wpisów.

Nie używa `READ_LOGS`, nie odczytuje całego systemowego Logcata i nie wysyła logów przez sieć.

### 5. Instrumentacja playbacku

`LegacyMobilePlaybackRepository` przekazuje do diagnostyki:

- start przygotowania media,
- źródło playbacku,
- stan ExoPlayera,
- przejściowy 403,
- błąd końcowy,
- aktywne formaty,
- Radio DVR,
- restart/reload playera.

Ta sama warstwa działa dla mobilnego playera i dedykowanego repozytorium Android Auto, ale raport rozróżnia właściciela playbacku.

### 6. Instrumentacja DeArrow

`MobileMetadataEnhancer` raportuje:

- liczbę wpisów w cache metadanych,
- typ ostatniego zadania (`DeArrow`, `OriginalTitle`, albo oba),
- liczbę elementów,
- czas wykonania,
- wynik OK/ERROR.

## Prywatność

Diagnostyka nie dodaje żadnego nowego połączenia sieciowego.

Do raportu nie trafia pełny podpisany URL streamu. Zapisywany jest tylko host, np. domena CDN. YouTube media ID jest maskowane do ostatnich czterech znaków. Tytuł pozostaje w raporcie, ponieważ jest przydatny przy ręcznym zgłaszaniu problemu; raport jest jednak tylko lokalny do chwili ręcznego skopiowania.

## Zmodyfikowane obszary

- `common/.../MobileDiagnostics.java`
- nowy pakiet `nativeui/diagnostics/`
- nowy `DiagnosticsFragment`
- nawigacja i ekran głównych ustawień,
- `LegacyMobilePlaybackRepository`,
- `MobileMetadataEnhancer`,
- `SmartTubeMobileNativeProvider`,
- `RadioTimeShiftController` / `RadioDvrProxy`,
- zasoby PL/EN.

## Checklista po kompilacji

1. Otwórz `Ustawienia → Diagnostyka`.
2. Sprawdź, że raport pokazuje wersję aplikacji i urządzenie.
3. Uruchom zwykły film, wróć do Diagnostyki i sprawdź format video/audio oraz czasy prepare.
4. Zmień jakość filmu i sprawdź, czy aktualny format w raporcie się zmienił.
5. Jeżeli pojawi się przejściowy 403, sprawdź `transient_403`, `recovered_403` i `playback_reloads_or_retries`.
6. Włącz Radio i sprawdź `RadioDVR active`, długość okna i `bytes`.
7. Otwórz listę z aktywnym DeArrow i sprawdź `metadata_cache_entries` / `last_metadata_fetch`.
8. Użyj `Kopiuj raport` i wklej tekst do notatnika.
9. Wyłącz `Rejestruj dane diagnostyczne`, użyj aplikacji przez chwilę i potwierdź, że nowe liczniki/zdarzenia nie przyrastają.
10. Wyczyść diagnostykę i sprawdź wyzerowanie liczników.

## Walidacja statyczna wykonana przed spakowaniem

- wszystkie XML-e `stmobile/res` parsują się poprawnie,
- brak duplikatów nazw zasobów w nowych plikach strings PL/EN,
- kontrola delimiterów i literałów w zmienionych plikach Java,
- kontrola listy zmienionych plików względem paczki bazowej,
- test integralności końcowego ZIP-a.

Pełny build Android nie został wykonany w tym środowisku: Android SDK nie jest skonfigurowany, a wrapper projektu wymaga Gradle 7.5, którego dystrybucja nie jest lokalnie zcache’owana. Próba `bash ./gradlew --offline --version` kończy się próbą pobrania `services.gradle.org`, ale środowisko nie ma dostępu DNS do internetu. Kod jest przygotowany do kompilacji po stronie użytkownika.
