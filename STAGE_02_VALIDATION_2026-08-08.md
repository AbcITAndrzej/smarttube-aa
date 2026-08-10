# Walidacja — Stage 02 Instant Play

Baza: `smarttube-aa-stage-01-diagnostics-2026-08-08`

## Wyniki kontroli

- XML: **149/149** plików projektu poprawnie sparsowanych.
- `PlayerSettingsFragment`: **25** odwołań `R.id`, **0** brakujących ID.
- Nowe stringi Instant Play: obecne w `values` i `values-pl`, bez duplikatów.
- Zmienione pliki Java: kontrola par nawiasów klamrowych **6/6 OK**.
- Nowe klasy `MobileInstantPlayController`, `MobileInstantPlayPreferences` oraz rozszerzony `MobileFeatureFlags`: kompilacja `javac` z minimalnymi stubami Android API **OK**.
- Izolacja AA: brak importów/odwołań `MobileInstantPlay*` w pakiecie `automotive`.
- Sieć: Stage 02 nie dodaje żadnych nowych URL-i/hostów/endpointów.
- Poprzednie poprawki: zachowany mobilny suppress recoverowalnego 403, integracja segmentów SponsorBlock i brak twardego filtra `countrycode=PL` w Radiu.
- Patch: zastosowany na czystym Stage 01; wynikowe drzewo było **identyczne** z Stage 02.
- Pełny build Android: nie wykonany lokalnie. Wrapper wymaga Gradle 7.5 z `services.gradle.org`, a środowisko nie ma dostępu do tej dystrybucji.

## Co należy sprawdzić po kompilacji

1. Zwykły film bez 403 — brak zauważalnego opóźnienia względem Stage 01.
2. Film z przejściowym 403 — brak natychmiastowego komunikatu 403; automatyczne recovery.
3. Shorts z przejściowym 403 — recovery nie może przełączyć na poprzedni Short.
4. Szybkie przełączanie kilku filmów — stare delayed retry muszą zostać unieważnione przez numer sesji.
5. Ustawienia `Odtwarzacz -> Instant Play` — wszystkie trzy przełączniki działają niezależnie i domyślnie są ON.
6. Wyłączenie mastera Instant Play — zachowanie wraca do wspólnego recovery SmartTube z Stage 01.
7. Radio/Radio DVR — brak zmiany zachowania.
8. Android Auto audio/radio — brak zmiany zachowania.
9. Diagnostyka — pola `instant_play_*` i liczniki zwiększają się tylko w odpowiednich sytuacjach.
