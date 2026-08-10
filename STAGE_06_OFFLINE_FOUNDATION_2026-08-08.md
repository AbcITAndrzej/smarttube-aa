# Stage 06 — Offline foundation / OfflineMediaRepository

## Cel etapu

Etap 06 buduje bezpieczny fundament pod późniejsze „słucham i zapisuję”, playlisty offline i bibliotekę offline w Android Auto. Ten etap **nie pobiera jeszcze żadnych danych z internetu i nie zmienia źródła odtwarzania**. Ma przygotować spójne API, prywatny magazyn plików, bazę metadanych oraz politykę miejsca na dysku, żeby Etap 07 nie dokładał logiki pobierania „na dziko” bez kontroli stanu i limitów.

## Najważniejsza zasada

- brak nowych endpointów i brak nowego serwera,
- brak automatycznego downloadu w Stage 06,
- brak integracji ze stabilnym Android Auto w tym etapie,
- brak zapisywania podpisanych/krótkotrwałych URL-i YouTube w bazie,
- pliki audio są przygotowane do zapisu w prywatnym `noBackupFilesDir`, więc nie wymagają uprawnień do pamięci współdzielonej i nie trafiają do backupu aplikacji.

## Nowe klasy

### `OfflineMediaRepository`
Centralne API przyszłego systemu offline. Obsługuje:

- rezerwację elementu przed pobraniem,
- stan `DOWNLOADING`,
- otwarcie prywatnego pliku `.part`,
- aktualizację postępu,
- atomowe zakończenie pobrania (`.part` -> `.audio`),
- stan `AVAILABLE`,
- oznaczenie `FAILED` / `EXPIRED`,
- odczyt dostępnego pliku,
- LRU touch po użyciu,
- usuwanie pojedynczego elementu,
- czyszczenie / reconcile,
- statystyki pamięci.

Repozytorium **nie wykonuje requestów HTTP**. Etap 07 będzie dostarczał do niego bajty wybranej ścieżki audio.

### `OfflineMediaDatabase`
Oddzielna baza SQLite `smarttube_mobile_offline.db`.

Tabela `offline_media` przechowuje:

- `media_id`,
- tytuł,
- autora,
- URL miniatury,
- długość materiału,
- MIME/codec audio,
- bezpieczny klucz pliku,
- bajty pobrane / oczekiwane,
- stan,
- krótki opis błędu,
- czas utworzenia / aktualizacji / ostatniego użycia,
- znacznik wygaśnięcia.

Nie zapisuje podpisanego URL-a streamu.

### `OfflineAudioStore`
Prywatny magazyn:

`<noBackupFilesDir>/offline_audio_v1/`

Nazwy nie zawierają `mediaId`. ID jest mapowane do deterministycznego SHA-256:

- `<hash>.part` — dane w trakcie pobierania,
- `<hash>.audio` — ukończony lokalny plik.

Promocja `.part -> .audio` wykonywana jest w tej samej lokalizacji. Gdy rename nie zadziała, kod używa bezpiecznego copy + cleanup.

### `OfflineStorageManager`
Pilnuje dwóch warunków jednocześnie:

1. limit danych offline użytkownika,
2. minimalna ilość wolnego miejsca, która ma zostać na urządzeniu.

Domyślne wartości:

- limit offline: **2 GB**,
- rezerwa wolnego miejsca: **512 MB**,
- automatyczne czyszczenie: **ON**.

Dostępne limity: 1 / 2 / 5 / 10 GB.

Dostępna rezerwa: 256 MB / 512 MB / 1 GB.

Kolejność usuwania:

1. `EXPIRED`,
2. `FAILED`,
3. najdawniej używane `AVAILABLE` (LRU), tylko gdy nadal trzeba odzyskać miejsce.

`DOWNLOADING` nie jest kandydatem do automatycznej ewikcji.

### Modele

- `OfflineMediaState`: `DOWNLOADING`, `AVAILABLE`, `FAILED`, `EXPIRED`,
- `OfflineMediaDescriptor`,
- `OfflineMediaRecord`,
- `OfflineMediaStats`,
- `OfflineCleanupResult`.

## Ustawienia

Dodano główną pozycję:

`Ustawienia -> Offline`

Stage 06 pokazuje:

- `Włącz fundament trybu offline` — domyślnie ON,
- `Automatyczne czyszczenie` — domyślnie ON,
- limit pamięci,
- minimalną rezerwę wolnego miejsca,
- liczbę rekordów w stanach AVAILABLE/DOWNLOADING/FAILED/EXPIRED,
- śledzony rozmiar,
- wolne miejsce urządzenia,
- `Uruchom czyszczenie teraz`,
- `Wyczyść wszystkie dane offline` z potwierdzeniem.

Opis w ekranie jasno informuje, że **automatyczne zapisywanie podczas słuchania pojawi się dopiero w Stage 07**.

## FeatureFlag

Dodano:

`OFFLINE_FOUNDATION`

Domyślnie: `true`.

Jest widoczny w `Ustawienia -> Diagnostyka -> Stage 6`.

Efektywna aktywacja repozytorium wymaga jednocześnie:

- ustawienia użytkownika `foundation_enabled=true`,
- rollout flag `OFFLINE_FOUNDATION=true`.

Dzięki temu można awaryjnie wyłączyć kod Stage 06 bez kasowania danych ani ustawień użytkownika.

## Diagnostyka

Raport zawiera teraz m.in.:

- effective / preference dla Offline foundation,
- auto-cleanup,
- liczby rekordów wg stanu,
- śledzony rozmiar danych,
- limit,
- rezerwę,
- aktualne wolne miejsce urządzenia,
- stan FeatureFlaga.

Raport nadal jest lokalny i nie jest wysyłany automatycznie.

## Izolacja Android Auto

Stage 06 nie zmienia:

- `SmartTubeAutoMusicService`,
- katalogu AA,
- playbacku AA,
- Radio DVR,
- playera VOD/Shorts.

Offline w Android Auto jest osobnym późniejszym Stage 09.

## Testy dodane do projektu

- `OfflineFileKeyTest` — deterministyczne i bezpieczne nazwy plików,
- `OfflineMediaPreferencesTest` — bezpieczne wartości domyślne i normalizacja limitów,
- `OfflineMediaRepositoryTest` — lifecycle `DOWNLOADING -> AVAILABLE` oraz cleanup `FAILED`.

## Checklista testu po kompilacji

1. Otwórz `Ustawienia -> Offline`.
2. Potwierdź domyślnie: fundament ON, auto-cleanup ON, limit 2 GB, rezerwa 512 MB.
3. Zmień limit na 1/5/10 GB i wróć do ekranu — wartość powinna zostać.
4. Zmień rezerwę 256/512 MB/1 GB.
5. `Uruchom czyszczenie teraz` przy pustej bazie nie może wywołać crasha.
6. `Wyczyść wszystkie dane offline` wymaga potwierdzenia.
7. Otwórz `Diagnostyka` i sprawdź `OfflineFoundation` / `OfflineStorage`.
8. Wyłącz FeatureFlag Stage 6 — ekran Offline powinien raportować efektywnie OFF mimo pozostawionego ustawienia użytkownika ON.
9. Włącz flagę ponownie.
10. Sprawdź, że odtwarzanie VOD, Shorts, Radio i Android Auto zachowuje się dokładnie jak w Stage 05 — Stage 06 nie jest jeszcze podłączony do playbacku.

## Co celowo NIE jest częścią Stage 06

- przechwytywanie bajtów podczas streamingu,
- pobieranie utworu w tle,
- WorkManager/DownloadService,
- „Pobierz offline” przy playliście,
- odtwarzanie offline,
- fallback online -> lokalny plik,
- katalog Offline w Android Auto.

Te elementy zaczynają się od Stage 07 i kolejnych etapów.
