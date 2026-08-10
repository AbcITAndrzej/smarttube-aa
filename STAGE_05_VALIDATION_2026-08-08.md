# Stage 05 — raport walidacji

Walidacja wykonana na drzewie `smarttube-aa-stage-05-radio-2-2026-08-08` przed utworzeniem finalnego ZIP-a.

## Kontrole wykonane

- XML: wszystkie pliki `smarttubetv/src/stmobile/res/**/*.xml` parsują się poprawnie.
- Zasoby: odwołania `R.*` z Java zmienionych w Stage 05 zostały porównane ze wszystkimi zasobami projektu; brak nowych brakujących identyfikatorów.
- PL/EN: wszystkie nowe stringi Radia/Radio 2.0 mają odpowiedniki w `values` i `values-pl`; sprawdzono zgodność numerowanych placeholderów `%n$...`.
- Java syntax: wszystkie 11 zmienionych plików Java przepuszczono przez parser `javac`; brak błędów składniowych.
- Radio cluster compile: `RadioPreferences`, `RadioStation`, `RadioTimeShiftController`, `RadioDvrProxy` i `RadioStationRepository` skompilowano razem z minimalnymi stubami Android/diagnostyki/JSON; wynik `RADIO_CLUSTER_COMPILE_OK`.
- Kumulatywność: sprawdzono obecność kluczowych markerów Stage 01–04 oraz pierwszych poprawek (paginator, Instant Play, Diagnostyka, Smart Player, Radio DVR, DeArrow mobile, 403 suppression, `touchSlop`, brak `countrycode=PL`).
- Sieć: Stage 05 nie wprowadza nowego dostawcy API. Nowe wyszukiwanie/failover używa istniejącego `de1.api.radio-browser.info`; odtwarzanie nadal łączy się z hostem streamu stacji.
- Cache cursor: sprawdzono, że zewnętrzne wyniki search nie przesuwają kursora `nextOffset` pełnego katalogu Stage 03 po restarcie.
- Cache growth: powtarzany search nie dopisuje ponownie znanych URL-i do NDJSON.
- Personal data cache: ręczna synchronizacja zachowuje lokalne stacje należące do Ulubionych/Ostatnich.
- Android Auto queue isolation: browser ID filtrowanych kolejek zawiera hash kontenera, co zapobiega kolizji tej samej stacji pomiędzy różnymi gatunkami/search.
- Patch scope: Stage 05 obejmuje 18 zmienionych/dodanych plików względem czystego Stage 04 (kod, zasoby i dokumentacja).
- Patch quality: `git diff --check` przechodzi bez błędów whitespace.
- Patch round-trip: finalny patch jest weryfikowany przez `git apply --check`, zastosowanie do czystego Stage 04 i porównanie drzewa 1:1 z Stage 05.
- ZIP: finalna paczka jest weryfikowana przez `unzip -t`.

## Czego nie wykonano

Nie wykonano pełnego `assembleStmobileDebug`. To środowisko nie ma gotowego Android SDK/zgodnego lokalnego toolchainu projektu, a wrapper wymaga zewnętrznej dystrybucji Gradle. Pełna kompilacja i runtime pozostają do wykonania po stronie użytkownika.

## Zalecane polecenia po rozpakowaniu

```bash
./gradlew :smarttubetv:testStmobileDebugUnitTest
./gradlew :smarttubetv:assembleStmobileDebug
```

Jeżeli projekt używa innej nazwy wariantu w Twoim środowisku, użyj odpowiadających tasków `test...` i `assemble...` z `./gradlew tasks`.
