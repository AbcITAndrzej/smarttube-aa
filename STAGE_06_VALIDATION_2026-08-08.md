# Stage 06 — raport walidacji statycznej

## Wynik

Stage 06 przygotowano na bazie pełnej paczki:

`smarttube-aa-stage-05-radio-2-2026-08-08.zip`

## Kontrole wykonane przed spakowaniem

- parsowanie wszystkich XML w `smarttubetv/src/stmobile/res`,
- kontrola duplikatów zasobów w `values` i `values-pl`,
- kontrola nowych ID/layout/string używanych przez Stage 06,
- `git diff --check`,
- kompilacja czystych klas modelu/haszowania przez `javac`,
- kompilacja całego pakietu `nativeui/offline` przez `javac` z minimalnymi stubami Android SQLite/Context/StatFs,
- parse-check zmienionych klas UI/diagnostyki przez `javac` (brak błędów składni),
- kontrola braku nowych URL/endpointów w kodzie Stage 06,
- test zastosowania patcha Stage 06 do czystego Stage 05,
- porównanie drzewa po patchu z finalnym Stage 06,
- `unzip -t` finalnej paczki.

## Build Gradle

Pełnego `:smarttubetv:testStmobileDebugUnitTest` / `assembleStmobileDebug` nie można wykonać w tym środowisku. Wrapper wymaga dystrybucji Gradle 7.5 i próbuje pobrać ją z `services.gradle.org`; środowisko nie ma dostępu DNS/internet do tego hosta.

Log kończy się `UnknownHostException: services.gradle.org`.

## Ograniczenie runtime

Stage 06 jest fundamentem i sam nie wykonuje pobierania. Runtime test faktycznego transferu/cache-through zaczyna się w Stage 07.
