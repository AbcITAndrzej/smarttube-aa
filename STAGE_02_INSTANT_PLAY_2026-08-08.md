# Etap 02 — Instant Play / stabilniejszy start filmu

Baza: `smarttube-aa-stage-01-diagnostics-2026-08-08.zip`

Ten etap nie usuwa żadnej wcześniejszej poprawki. Dodaje mobilną warstwę bezpieczeństwa startu VOD/Shorts nad istniejącym mechanizmem `ErrorFixerController` SmartTube. Radio i stabilny Android Auto są celowo wyłączone z nowej logiki.

## Co zostało dodane

### 1. Mobile Instant Play

Nowa klasa `MobileInstantPlayController` pilnuje pojedynczej sesji startowej filmu:

`PREPARING -> READY -> PLAYING`

Nie zastępuje istniejącego recovery SmartTube. Dla przejściowego 403 standardowy `ErrorFixerController` nadal:

1. wykrywa błąd źródła,
2. wykonuje `YouTubeServiceManager.applyNoPlaybackFix()`,
3. odświeża źródło i wykonuje szybkie `reloadVideoAfterStreamRefresh()`.

Warstwa Instant Play czeka. Dopiero jeśli player nadal nie osiągnął `READY`, uruchamia dodatkowy fallback.

### 2. Fallback po przejściowym HTTP 403

- standardowe recovery SmartTube ma pierwszeństwo,
- pierwszy mobilny fallback: kontrola po ok. 1,25 s,
- drugi fallback: kontrola po ok. 3,25 s,
- jeśli player jest już `READY`, zaplanowane retry są anulowane,
- maksymalnie dwa mobilne fallbacki dla jednego przygotowania filmu,
- recoverowalny 403 nadal nie jest pokazywany użytkownikowi jako natychmiastowy błąd.

To ogranicza podwójne/równoległe przeładowania i nie tworzy nieskończonej pętli retry.

### 3. Watchdog startu

Dla mobilnego VOD/Shorts:

- po 8 sekundach bez `READY` wykonywane jest jedno kontrolowane przeładowanie metadanych/formatu,
- po 22 sekundach bez `READY` diagnostyka zapisuje timeout i UI otrzymuje retryowalny komunikat zamiast nieskończonego cichego spinnera,
- engine nie jest brutalnie zwalniany po timeout — wspólna warstwa SmartTube może nadal odzyskać odtwarzanie.

### 4. Izolacja

Instant Play jest aktywny tylko gdy:

- materiał nie jest Radiem,
- repozytorium nie działa w trybie headless Android Auto,
- użytkownik ma włączony Instant Play,
- wewnętrzny FeatureFlag jest włączony.

`SmartTubeAutoMusicService` nie korzysta z preferencji Instant Play i nie zmieniono jego logiki.

### 5. Nowe ustawienia

`Ustawienia -> Odtwarzacz -> Instant Play`

Domyślnie włączone, ale każde można wyłączyć:

- `Włącz inteligentny start filmu`,
- `Dodatkowy fallback po przejściowym 403`,
- `Watchdog: ponów start, gdy player utknie przed READY`.

Reset ustawień Odtwarzacza resetuje również te trzy opcje. Android Auto pozostaje bez zmian.

### 6. FeatureFlags

Dodane bramki rollout/rollback:

- `instant_play`,
- `instant_play_forbidden_recovery`,
- `instant_play_startup_watchdog`.

Domyślna wartość wszystkich: `true`.

### 7. Diagnostyka

Raport Stage 01 został rozszerzony o:

- bieżący stan Instant Play,
- liczbę mobilnych fallbacków 403 dla bieżącego filmu,
- liczbę watchdog reloadów,
- czas do READY,
- ostatni timeout,
- trwałe liczniki fallbacków 403,
- trwałe liczniki watchdog reloadów,
- trwałe liczniki timeoutów,
- stan wszystkich ustawień i FeatureFlags Instant Play.

Tag logów: `P18-InstantPlay`.

## Czego celowo nie zrobiono

- Nie zmieniono stabilnej logiki Android Auto.
- Nie zmieniono Radia i Radio DVR.
- Nie dodano agresywnego prefetchu podpisanych URL-i YouTube. Takie URL-e wygasają i zbyt wczesny prefetch mógłby zwiększyć liczbę 403 zamiast ją zmniejszyć.
- Nie usunięto wspólnego `ErrorFixerController`; Instant Play jest tylko opóźnionym zabezpieczeniem.
- Nie dodano nowych połączeń z zewnętrznymi serwerami.

## Checklista testów na urządzeniu

1. Otwórz `Ustawienia -> Odtwarzacz` i sprawdź sekcję Instant Play.
2. Uruchom kolejno kilka zwykłych filmów.
3. Uruchom kilka Shorts.
4. Zmieniaj szybko filmy jeden po drugim — retry poprzedniego filmu nie może przejąć nowej sesji.
5. Jeżeli wystąpi 403, nie powinien pojawić się natychmiastowy toast z 403; film powinien sam odzyskać źródło.
6. Sprawdź `Ustawienia -> Diagnostyka` i pola `instant_play_*`.
7. Wyłącz `Dodatkowy fallback po przejściowym 403` i potwierdź, że standardowe recovery SmartTube nadal działa.
8. Wyłącz cały `Instant Play` i potwierdź powrót do zachowania z Stage 01.
9. Uruchom Radio i Radio DVR — zachowanie nie może się zmienić.
10. Uruchom Android Auto audio/radio — zachowanie nie może się zmienić.

## Kompilacja

Zalecane:

```bash
./gradlew :smarttubetv:testStmobileDebugUnitTest
./gradlew :smarttubetv:assembleStmobileDebug
```

W środowisku przygotowującym paczkę wrapper Gradle 7.5 nie mógł zostać pobrany z `services.gradle.org`, dlatego pełny Android build nie został wykonany tutaj.
