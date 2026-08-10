# Stage 10 — Smart Trip Reserve / inteligentny zapas na podróż

Data: 2026-08-08
Baza: `smarttube-aa-stage-09-offline-android-auto-2026-08-08.zip`

## Cel

Etap 10 dodaje opcjonalny mechanizm, który może sam utrzymywać na urządzeniu rozsądny zapas audio do jazdy bez internetu. Nie tworzy kolejnego downloadera. Planer buduje małe, syntetyczne playlisty offline, a faktyczny transfer deleguje do sprawdzonego downloadera audio-only z Etapu 08 oraz magazynu z Etapu 06.

Funkcja jest **domyślnie wyłączona**, ponieważ może zużywać transfer i pamięć. Techniczny FeatureFlag pozostaje domyślnie włączony, aby użytkownik mógł uruchomić funkcję jednym przełącznikiem.

## Co utrzymuje offline

Po włączeniu można utrzymywać trzy niezależne grupy:

1. **Ostatnio słuchane** — domyślnie 30, do wyboru 10/20/30/50.
2. **Polubione / Liked Music** — domyślnie 20, do wyboru 10/20/50/100; tę część można całkowicie wyłączyć.
3. **Ostatnio używane playlisty** — domyślnie 2 playlisty, do wyboru 0/1/2/3; limit na jedną playlistę 25/50/100 utworów, domyślnie 50.

Automatyczne miksy/radia YouTube z identyfikatorami zaczynającymi się od `RD` lub `UL` nie są utrwalane jako „ostatnie playlisty”, ponieważ są zmienne i potencjalnie nieograniczone.

## Kiedy utwór trafia do historii podróży

Samo przypadkowe otwarcie filmu nie wystarcza. `OfflineTripReserveController` wymaga około **10 sekund faktycznego PLAYING**. Pauza nie nalicza czasu. Po osiągnięciu progu zapisywane są wyłącznie lokalne metadane:

- `mediaId`,
- tytuł,
- autor,
- URL miniatury jako metadata,
- długość,
- czas ostatniego odtworzenia,
- opcjonalnie identyfikator/nazwa playlisty.

Nie zapisujemy signed URL ani bezpośredniego URL strumienia.

Wykluczone są Radio, live, upcoming, Shorts oraz odtwarzanie już lokalnego pliku offline. Mechanizm działa zarówno dla normalnego nowego playera mobilnego, jak i headless playbacku używanego przez stabilny Android Auto, dzięki czemu trasa samochodem również może aktualizować historię.

## Architektura

### `OfflineTripReserveDatabase`

Osobna SQLite: `smarttube_mobile_offline_trip_reserve.db`.

Tabele:

- `trip_recent` — maksymalnie 400 ostatnich unikalnych materiałów,
- `trip_recent_playlists` — maksymalnie 40 ostatnich playlist.

Baza nie zawiera stream URL/signed URL.

### `OfflineTripReserveRepository`

Warstwa historii i polityki. Tworzy specjalne identyfikatory, które nie kolidują z playlistami użytkownika:

- `trip:recent`,
- `trip:favorites`,
- `trip:playlist:<oryginalnePlaylistId>`.

Po wyłączeniu Smart Trip Reserve syntetyczne playlisty przestają przypinać pliki. Historia słuchania pozostaje lokalnie, aby ponowne włączenie nie zaczynało od zera.

### `OfflineTripReserveService`

Lekki foreground `dataSync` **planer**, nie downloader bajtów. Zadania:

- synchronizacja `trip:recent`,
- odczyt Liked Music przez istniejący `ContentService.getMusicObserve()` i synchronizacja `trip:favorites`,
- pobranie metadanych ostatnich playlist przez istniejący `ContentService`,
- ograniczenie liczby elementów zgodnie z ustawieniami,
- usunięcie nieaktualnych syntetycznych kopii playlist,
- obudzenie istniejącego `OfflinePlaylistDownloadService`.

Liked Music odświeża się maksymalnie co ok. 6 godzin, chyba że użytkownik wybierze „Przygotuj zapas teraz” albo zmieni polubienie z Android Auto. Błąd/niezalogowane konto nie usuwa poprzedniej działającej kopii ulubionych.

### Stage 08 downloader

Cały transfer audio pozostaje w istniejącym mechanizmie:

- audio-only,
- signed URL pobierany just-in-time,
- brak trwałego zapisu signed URL,
- HTTP Range / `.part`,
- odświeżenie źródła po HTTP 403/410,
- limit/rezerwa dysku z Etapu 06,
- wspólny `OfflineDownloadCoordinator`,
- jedna kopia audio współdzielona przez ręczne playlisty, Listen & Save i Trip Reserve.

To ogranicza ryzyko trzech niezależnych implementacji pobierania tego samego materiału.

## Ustawienia

`Ustawienia -> Offline -> Inteligentny zapas na podróż`:

- **Włącz inteligentny zapas** — domyślnie OFF,
- **Tylko Wi‑Fi/Ethernet** — domyślnie ON,
- **Ostatnio słuchane** — 10/20/30/50, domyślnie 30,
- **Uwzględnij polubione** — domyślnie ON,
- **Liczba polubionych** — 10/20/50/100, domyślnie 20,
- **Ostatnie playlisty** — 0/1/2/3, domyślnie 2,
- **Utwory z jednej playlisty** — 25/50/100, domyślnie 50,
- **Przygotuj zapas teraz** — ręczna pełna synchronizacja planu.

Globalny limit offline (np. 2 GB) i minimalna wolna przestrzeń z Etapu 06 nadal są nadrzędne. Smart Trip Reserve nie ma prawa przekroczyć tych limitów.

## Android Auto

Etap 10 nie tworzy nowej MediaSession i nie modyfikuje architektury Stage 09. Pliki przygotowane przez Trip Reserve są zwykłymi plikami `OfflineMediaRepository`, więc automatycznie mogą być użyte przez istniejący fallback Android Auto po utracie internetu.

Zmiana Like/Unlike wykonana z Android Auto po potwierdzeniu przez konto wywołuje ręczne odświeżenie planera ulubionych. Jeżeli funkcja jest wyłączona, planner natychmiast kończy pracę.

## Sieć i prywatność

Etap 10 **nie dodaje nowego serwera ani własnego endpointu**. Korzysta wyłącznie z istniejącego w projekcie YouTube `ContentService`/`MediaItemService` i aktualnych bezpośrednich hostów audio, które już wykorzystywały Etapy 07/08.

Historia podróży jest lokalna. Trwale nie zapisujemy podpisanych URL-i CDN.

## Fail-safe

- wyłączenie głównego przełącznika powoduje zwolnienie syntetycznych pinów playlist Trip Reserve; pliki nie są brutalnie kasowane i mogą zostać później usunięte przez zwykłe LRU,
- techniczny FeatureFlag `OFFLINE_TRIP_RESERVE` pozwala awaryjnie wyłączyć Stage 10 bez wycinania kodu,
- wyłączenie ręcznych playlist Stage 08 nie musi wyłączać Trip Reserve — oba korzystają z tego samego technicznego downloadera, ale mają osobne preferencje użytkownika,
- aktywny transfer sprawdza, czy konkretna kolejka jest nadal dozwolona i przerywa się po wyłączeniu funkcji,
- gdy Liked Music jest niedostępne, poprzedni zapas polubionych pozostaje zamiast zostać skasowany.

## Diagnostyka

Raport pokazuje:

- effective/pref dla Trip Reserve,
- Wi‑Fi only,
- cele recent/favorites/playlists/tracks,
- liczbę pozycji w historii,
- liczbę ostatnich playlist,
- liczbę zarządzanych syntetycznych kolejek,
- czas ostatniej udanej synchronizacji Liked Music.

Logi: `P20-TripReserve`.

## Nowe/kluczowo zmienione pliki

- `OfflineTripReserveController.java`
- `OfflineTripReserveDatabase.java`
- `OfflineTripReservePlaylistRef.java`
- `OfflineTripReserveRepository.java`
- `OfflineTripReserveService.java`
- `OfflineMediaPreferences.java`
- `OfflinePlaylistRepository.java`
- `OfflinePlaylistDatabase.java`
- `OfflinePlaylistDownloadService.java`
- `LegacyMobilePlaybackRepository.java`
- `OfflineSettingsFragment.java`
- `OfflinePlaylistsFragment.java`
- `MobileFeatureFlags.java`
- `MobileDiagnosticsStore.java`
- `DiagnosticsFragment.java`
- `SmartTubeAutoMusicService.java`
- `mobile_offline_settings_fragment.xml`
- `mobile_diagnostics_fragment.xml`
- EN/PL strings,
- manifest,
- `OfflineTripReserveRepositoryTest.java`.

## Checklista testów po kompilacji

1. Po aktualizacji Stage 09 -> Stage 10 funkcja Trip Reserve jest domyślnie wyłączona.
2. Włącz ją i pozostaw „Tylko Wi‑Fi” — na danych komórkowych planner może ułożyć część lokalną, ale nie powinien rozpoczynać transferów wymagających sieci.
3. Odtwarzaj utwór krócej niż 10 s — nie powinien wejść do `trip:recent`.
4. Odtwarzaj ponad 10 s — historia powinna wzrosnąć, a zapas zostać zaplanowany.
5. Sprawdź target 10/20/30/50 ostatnich utworów.
6. Zalogowane konto: włącz „Polubione” i „Przygotuj teraz”; zweryfikuj utworzenie syntetycznej playlisty polubionych.
7. Niezalogowane konto: synchronizacja ulubionych ma zakończyć się bez crasha i nie usuwać istniejącego zapasu.
8. Odtwórz kilka materiałów z normalnej playlisty i sprawdź utworzenie `trip:playlist:*`.
9. Materiał z `RD...`/`UL...` nie powinien dodać trwałej „ostatniej playlisty”.
10. Zmień limit playlist 2 -> 1 -> 0; stare syntetyczne piny powinny zostać zwolnione po bezpiecznym zakończeniu aktywnego transferu.
11. Wyłącz Trip Reserve podczas pobierania — następna kontrola transferu powinna zatrzymać kolejkę Trip Reserve, bez naruszenia ręcznych playlist.
12. Ręcznie pobrana playlista z Etapu 08 powinna nadal działać niezależnie od Stage 10.
13. Ten sam utwór w Trip Reserve i ręcznej playliście powinien wykorzystywać jedną lokalną kopię.
14. Utrata internetu w Android Auto: jeśli utwór został wcześniej przygotowany przez Trip Reserve, Stage 09 powinien móc użyć lokalnego fallbacku.
15. Like/Unlike w AA: po potwierdzeniu operacji powinien pojawić się event planera `P20-TripReserve` (jeśli funkcja włączona).
16. Przekroczenie limitu storage ma nadal respektować Stage 06 i nie usuwać plików przypiętych do aktywnych playlist.
17. Diagnostyka pokazuje stan Stage 10 i nie zawiera signed URL.
18. Po wyłączeniu Trip Reserve inne funkcje Offline, Radio i Android Auto działają bez zmian.
