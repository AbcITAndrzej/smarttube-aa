# Stage 08 — raport walidacji

Data: 2026-08-08
Baza patcha: `smarttube-aa-stage-06-offline-foundation-2026-08-08`

> Etap 07 został przez użytkownika pominięty. Stage 08 jest celowo patchowany bezpośrednio na Stage 06.

## Kontrole wykonane przed spakowaniem

### Zasoby Android

- 73 pliki XML `stmobile` — wszystkie sparsowane poprawnie,
- `values/mobile_native_strings.xml`: 342 nazwane zasoby, 0 duplikatów,
- `values-pl/mobile_native_strings.xml`: 341 nazwanych zasobów, 0 duplikatów,
- skan aplikacyjnych `R.string`: 0 brakujących,
- skan aplikacyjnych `R.id`: 0 brakujących,
- skan aplikacyjnych `R.drawable`: 0 brakujących,
- skan aplikacyjnych `R.layout`: 0 brakujących.

Różnica jednego wpisu PL/EN jest wcześniejszym `aa_app_name` i nie została wprowadzona przez Stage 08.

### Java

- 26 nowych/zmienionych plików Java względem Stage 06,
- `javac -proc:none` bez Android/Gradle classpath: brak błędów gramatycznych/składniowych; pozostałe komunikaty są oczekiwanymi brakami klas Android/project dependencies,
- czyste modele kolejki (`OfflinePlaylistState`, `OfflinePlaylistItemState`, `OfflinePlaylistRecord`) — kompilacja `javac` PASS,
- osobny smoke-test `OfflineAudioFormatSelector` na minimalnych stubach — PASS: preferowany język wygrywa, OTF jest odrzucany, `clen`/codec są poprawnie interpretowane,
- dodane testy projektu: `OfflinePlaylistRecordTest` i `OfflineAudioFormatSelectorTest` (JUnit/Mockito).

### Sieć / prywatność danych technicznych

- nowe/zmienione produkcyjne klasy Stage 08: 0 nowo wpisanych na sztywno `http://`/`https://` endpointów,
- schema `smarttube_mobile_offline_playlists.db`: brak kolumny signed/media/stream URL,
- podpisany URL jest pobierany just-in-time z istniejącego `MediaItemService`, używany do bieżącego requestu i nie jest utrwalany,
- 403/410 powoduje ponowne pobranie `FormatInfo`,
- guard reprezentacji resetuje `.part`, jeżeli przy odświeżeniu zmieni się MIME/codec/finite length — nie doklejamy bajtów innego formatu,
- gdy nie można ustalić skończonego rozmiaru (`clen` ani HTTP Content-Length), transfer kończy się bezpiecznym błędem zamiast omijać limit dysku,
- po uzyskaniu realnego Content-Length Stage 06 ponownie weryfikuje limit i rezerwę wolnego miejsca przed zapisem,
- po proces-death, jeżeli `.part` ma już pełny spodziewany rozmiar, wykonywany jest lokalny atomowy commit zamiast błędnego Range requestu od EOF.

### Izolacja

- automatyczne „słucham i zapisuję” Stage 07 nie zostało dodane,
- zwykły mobile VOD/Shorts nadal korzysta ze zwykłych ID; offline audio jest uruchamiane wyłącznie przez `offline:<mediaId>`,
- Radio i Radio DVR nie zostały podłączone do downloadera playlist,
- headless Android Auto odrzuca `offline:` — integracja AA offline pozostaje na Stage 09,
- stabilny `SmartTubeAutoMusicService` nie został zastąpiony przez Stage 08.

### Odporność kolejki

- aktywny download jest idempotentny przy ponownym kliknięciu Download — nie nadpisujemy bazy kolejki pod pracującym serwisem,
- przerwany element wraca `DOWNLOADING -> PENDING` z zachowaniem `.part`,
- licznik attempts nie cofa się po pauzie/utracie sieci,
- serwis ma `START_STICKY`,
- `NetworkCallback` budzi kolejkę po powrocie dozwolonej sieci,
- Stage 8 przypina elementy playlist w storage managerze; LRU nie usuwa jawnie pobranej playlisty,
- usuwanie playlist korzysta z liczby referencji i nie kasuje audio współdzielonego z inną playlistą.

### Gradle / Android build

Próba:

```bash
bash ./gradlew :smarttubetv:testStmobileDebugUnitTest --offline --no-daemon
```

nie mogła rozpocząć właściwego builda, ponieważ wrapper nie ma lokalnie Gradle 7.5 i próbuje pobrać:

`https://services.gradle.org/distributions/gradle-7.5-bin.zip`

Środowisko zwraca `UnknownHostException: services.gradle.org`.

Dlatego pełny `testStmobileDebugUnitTest` / `assembleStmobileDebug` pozostaje do wykonania w środowisku użytkownika z Android SDK i Gradle 7.5.

## Patch i ZIP

Przed finalnym przekazaniem wykonywane są dodatkowo:

- `git diff --check`,
- test nałożenia patcha na czysty Stage 06,
- porównanie wynikowego drzewa 1:1 z Stage 08,
- `unzip -t` pełnej paczki,
- SHA-256 ZIP-a.

Wyniki tych kontroli są wymagane do wydania paczki i w finalnym komunikacie są raportowane jako PASS tylko po ich rzeczywistym wykonaniu.
