# SmartTube AA / mobile – pakiet funkcji 2026-08-08

Ten katalog jest pełnym drzewem źródłowym opartym na `smarttube-aa-fixed-2026-08-08` i zawiera kolejną warstwę zmian zaakceptowanych do wdrożenia:

1. osobna sekcja **Ustawienia → Odtwarzacz** dla nowego mobilnego playera,
2. preferowany język audio i napisów bez automatycznego wymuszania ścieżki,
3. nowoczesny Material Bottom Sheet do wyboru jakości / audio / napisów,
4. wyszukiwarka i wspólne Ulubione dla Radia,
5. eksperymentalny time-shift Radia (telefon + Android Auto),
6. odizolowana eksperymentalna aktywność wideo dla Android Auto / parked apps,
7. testy jednostkowe najważniejszych nowych reguł.

Poprzednie poprawki z `FIXES_2026-08-08.md` pozostają w drzewie projektu.

---

## 1. Ustawienia → Odtwarzacz

Dodano oddzielny ekran `PlayerSettingsFragment` oraz osobny magazyn ustawień `MobilePlayerPreferences` (`smarttube_mobile_player`).

Najważniejsza zasada architektoniczna: **Android Auto nie czyta `MobilePlayerPreferences`**. Stabilna usługa `SmartTubeAutoMusicService` nadal korzysta z własnej konfiguracji i własnego profilu `LegacyMobilePlaybackRepository`.

Konfigurowalne elementy mobilnego playera:

- automatyczne ukrywanie kontrolek,
- pinch-to-zoom,
- double tap ±10 s,
- poziomy swipe ±10 s,
- poprzedni / następny,
- szybkie opcje audio/napisów,
- przycisk napisów,
- przycisk lektora/audio,
- jakość,
- prędkość,
- dopasowanie obrazu,
- PiP,
- pełny ekran,
- „więcej opcji”.

Play/Pause, Wstecz i oś czasu są celowo zawsze dostępne, żeby użytkownik nie mógł skonfigurować nieobsługiwalnego odtwarzacza.

### Pliki

- `smarttubetv/src/stmobile/java/.../nativeui/player/MobilePlayerPreferences.java`
- `smarttubetv/src/stmobile/java/.../nativeui/fragment/PlayerSettingsFragment.java`
- `smarttubetv/src/stmobile/res/layout/mobile_player_settings_fragment.xml`
- zmiany w `MobileSettingsFragment`, `MobileNavigator`, `MobileFragmentNavigator`, `mobile_native_fragment_settings.xml`.

---

## 2. Domyślny lektor i domyślne napisy

Nowe ustawienia zapisują jedynie **preferencję UI**. Nie są wpisywane do klasycznych `PlayerData`, dlatego nie tworzą nowej automatycznej zmiany ścieżki przy starcie filmu.

Po otwarciu listy audio/napisów:

- preferowany język dostaje znacznik `★ Domyślna`,
- aktualnie odtwarzana ścieżka dostaje `✓ Aktywna`,
- jeśli preferowany język istnieje, lista od razu przewija się do tej pozycji,
- dopiero kliknięcie użytkownika wykonuje `selectAudioTrack()` / `selectSubtitleTrack()`.

Dla mobilnej instancji `LegacyMobilePlaybackRepository` wyłączono dodatkową legacy-logikę `applyPreferredAudio()`. Dla Android Auto pozostawiono dotychczasowe zachowanie, żeby nowa funkcja nie zmieniała stabilnej integracji AA.

### Nowy picker ścieżek

Surowy `AlertDialog.setSingleChoiceItems()` w głównym playerze został zastąpiony Material `BottomSheetDialog` + `RecyclerView` + karty ścieżek. Picker jest wspólny dla jakości, audio i napisów, ale znaczniki języka są używane tylko dla audio/napisów.

### Pliki

- `nativeui/player/PlayerLanguageCatalog.java`
- `nativeui/player/PreferredTrackResolver.java`
- `nativeui/player/TrackPickerBottomSheet.java`
- `res/layout/mobile_track_picker_sheet.xml`
- `res/layout/mobile_track_picker_row.xml`
- `res/drawable/mobile_bottom_sheet_handle.xml`
- zmiany w `MobilePlaybackFragment.java` i `LegacyMobilePlaybackRepository.java`.

---

## 3. Radio – wyszukiwanie i Ulubione

Ulubione istniały już w `RadioStationRepository`; zostały zachowane jako jedno źródło prawdy dla telefonu i Android Auto. Repozytorium emituje zmianę katalogu, a AA odświeża odpowiednie węzły MediaBrowser po zmianie ulubionej stacji.

Dodano wyszukiwanie lokalne po aktualnie zsynchronizowanym katalogu:

- nazwie stacji,
- kraju,
- kodzie kraju,
- kodeku,
- tagach.

Wyszukiwanie działa offline na zapisanym cache i nie wykonuje requestu sieciowego przy każdym znaku.

**Uwaga:** katalog pozostaje celowo ograniczony do 200 globalnych, działających, najpopularniejszych stacji pobieranych przez obecny endpoint Radio Browser. Nie wysyłamy dziesiątek tysięcy rekordów jednorazowo do telefonu/MediaBrowser AA. Jeżeli w kolejnym etapie ma być pełny katalog, najlepiej dodać paginację i foldery krajów zamiast usuwania limitu.

### Pliki

- zmiany w `RadioStationRepository.java`,
- zmiany w `RadioSettingsFragment.java`,
- zmiany w `mobile_radio_settings_fragment.xml`.

---

## 4. Radio – Time-shift / DVR

Dodano eksperymentalny, **wyłącznie radiowy** bufor kroczący. VOD nie korzysta z tego kodu.

### Architektura

Dla progresywnych strumieni MP3/AAC/OGG:

```text
Internetowa stacja
      ↓
RadioDvrProxy
      ↓
24 MB maks. / 1, 3 lub 5 minut
      ↓
localhost 127.0.0.1:<losowy_port>/radio
      ↓
LegacyMobilePlaybackRepository / ExoPlayer
```

`RadioDvrProxy` utrzymuje bufor w RAM w małych fragmentach. Na seek otwierany jest nowy lokalny URL zaczynający się od fragmentu najbliższego żądanej pozycji. Gdy odtwarzacz dochodzi do końca zachowanego materiału, proxy dalej „tailuje” nowe dane ze stacji.

Konfiguracja w ekranie Radio:

- Time-shift włącz/wyłącz,
- 1 min,
- 3 min (domyślnie),
- 5 min.

Dodatkowe zabezpieczenia:

- limit pamięci 24 MB,
- brak wpływu na VOD,
- fail-open: jeżeli DVR nie zadziała, repozytorium przełącza się na bezpośredni URL stacji,
- HLS (`m3u8` / MPEGURL) nie jest przepuszczany przez własny proxy; pozostaje natywnym live streamem ExoPlayera. Jeżeli HLS udostępnia natywne okno seek, stary player może je wykorzystać, ale własny ring-buffer go nie emuluje.

### Telefon

Mobilny ekran radia wykorzystuje istniejący `SeekBar` jako okno kroczące. Dodano przycisk `LIVE`, który wraca na aktualny koniec bufora.

### Android Auto

`SmartTubeAutoMusicService` nadal używa stabilnego MediaSession/MediaBrowser, ale dla pozycji radiowej:

- `ACTION_SEEK_TO` kierowany jest bezpośrednio do radiowego DVR,
- `seekTo(0)` oznacza początek bieżącego bufora, a nie „poprzedni utwór”,
- po wyborze stacji nie jest wykonywany legacy `seekTo(0)`, bo stacja ma zacząć na live edge,
- rolling duration nie jest traktowany jako koniec utworu, więc nie uruchamia Auto Next,
- publiczna długość metadata jest kwantowana co 5 s, żeby nie przebudowywać karty AA przy każdym ticku.

### Znane ograniczenia eksperymentalnego DVR

1. Progresywny stream może zacząć się w środku ramki MP3/AAC. ExoPlayer zwykle potrafi ponownie zsynchronizować extractor, ale nie każda stacja zachowa się poprawnie.
2. Niektóre serwery wymagają własnych nagłówków/tokenów lub zwracają playlistę mimo URL wyglądającego jak zwykły stream. W takim przypadku działa fail-open do bezpośredniego playbacku.
3. Estymacja czasu wykorzystuje bitrate stacji z katalogu. Niepoprawny bitrate oznacza nieidealne wskazanie czasu, ale nie powinien uszkodzić samego audio.
4. Bufor jest w RAM i znika po zmianie stacji, zwolnieniu playera lub zakończeniu procesu.

### Pliki

- `nativeui/radio/RadioPreferences.java`
- `nativeui/radio/RadioTimeShiftController.java`
- `nativeui/radio/RadioDvrProxy.java`
- zmiany w `LegacyMobilePlaybackRepository.java`,
- zmiany w `SmartTubeAutoMusicService.java`,
- zmiany w playerze i ekranie ustawień Radia.

---

## 5. Android Auto – eksperymentalne wideo

Wdrożenie jest **clean-room** i nie kopiuje klas/kodu AABrowser.

Założenia bezpieczeństwa zaakceptowanego planu:

- stabilny `SmartTubeAutoMusicService` pozostaje oddzielnym MediaBrowserService,
- eksperymentalne wideo jest osobną `ExperimentalCarVideoActivity`,
- aktywność jest `android:enabled="false"` w manifeście,
- użytkownik musi jawnie włączyć ją w Ustawienia → Android Auto,
- przełącznik tylko włącza/wyłącza komponent przez `PackageManager`, bez ubijania procesu,
- aktywność używa kategorii `android.intent.category.CAR_LAUNCHER`,
- **nie** deklarujemy fałszywego `appCategory="game"`, `NAVIGATION`, `APP_MAPS` ani `distractionOptimized=true`,
- aktywność używa istniejącego mobilnego UI/playera i nie współdzieli publicznej MediaSession stabilnego AA audio.

Kod sprawdza także diagnostycznie, czy aktywność jest wyświetlana na innym niż domyślny display, ale nie traktuje tego jako obejścia platformy.

### Ważne ograniczenie platformowe

Ta implementacja jest przygotowana na legalny/obsługiwany mechanizm parked-app, ale **nie próbuje wymuszać widoczności aplikacji w wersjach Android Auto, które nie udostępniają kategorii wideo**. To zamierzone. Jeżeli po włączeniu eksperymentu aktywność nie pojawi się w launcherze samochodu, nie oznacza to regresji stabilnego AA – host po prostu nie udostępnił tego typu parked activity.

### AABrowser – dlaczego nie skopiowano implementacji 1:1

Repozytorium referencyjne:

- https://github.com/kododake/AABrowser
- manifest: https://github.com/kododake/AABrowser/blob/main/app/src/main/AndroidManifest.xml
- licencja: https://github.com/kododake/AABrowser/blob/main/LICENSE

AABrowser wykorzystuje klasyfikacje/ustawienia, których nie chcemy przenosić do stabilnej aplikacji, a jego kod jest na GPLv3. Dlatego w tym pakiecie wykorzystano tylko ogólną ideę oddzielnej powierzchni samochodowej i napisano niezależny kod.

Oficjalny punkt odniesienia do parked apps:

- https://developer.android.com/training/cars/parked/auto

### Pliki

- `automotive/ExperimentalCarVideoActivity.java`
- `automotive/ExperimentalCarVideoGate.java`
- zmiany w `AndroidAutoPreferences.java`,
- zmiany w `AndroidAutoSettingsFragment.java`,
- zmiany w `MobileNativeActivity.java`,
- zmiany w `src/stmobile/AndroidManifest.xml`,
- zmiany w `mobile_android_auto_settings_fragment.xml`.

---

## 6. Testy dodane do projektu

Dodano:

- `PreferredTrackResolverTest` – sprawdza preferowany język, język regionalny i `system`,
- `RadioPreferencesTest` – sprawdza domyślne 3 min oraz normalizację 1/3/5 min.

Istniejące testy `testStmobile` pozostają bez zmian.

---

## 7. Kompilacja po Twojej stronie

Projekt ma flavor `stmobile`. Najbardziej użyteczne komendy:

```bash
./gradlew :smarttubetv:testStmobileDebugUnitTest
./gradlew :smarttubetv:assembleStmobileDebug
```

Dla release użyj istniejącej konfiguracji podpisu projektu, np. odpowiedniego zadania `assembleStmobileRelease`.

W środowisku, w którym przygotowano tę paczkę, nie ma Android SDK ani dostępnej lokalnie dystrybucji Gradle 7.5; wrapper nie może pobrać jej z internetu. Z tego powodu **nie deklaruję pełnego builda jako wykonanego**. Wykonane kontrole statyczne opisuje `VALIDATION_2026-08-08.md`.

---

## 8. Zalecana kolejność testów manualnych

### Odtwarzacz

1. Ustawienia → Odtwarzacz.
2. Wyłącz kilka elementów, otwórz film i sprawdź, czy tylko te przyciski zniknęły.
3. Sprawdź, że Play/Pause, Wstecz i seekbar nadal istnieją.
4. Wyłącz pinch/double tap/swipe i sprawdź każdy gest osobno.
5. Ustaw `polski` dla lektora i napisów.
6. Uruchom film wielojęzyczny.
7. Otwórz audio/napisy: lista ma przewinąć się do polskiej pozycji i oznaczyć ją `★`, ale ścieżka nie może zmienić się przed kliknięciem.
8. Sprawdź zoom + pojedynczy tap – kontrolki nadal muszą znikać.
9. Sprawdź film 1080p/1440p/2160p i zmianę jakości – wcześniejsza poprawka layoutu seekbara nie może się cofnąć.

### Radio – telefon

1. Zsynchronizuj katalog.
2. Wpisuj nazwę/kraj/tag w wyszukiwarce.
3. Dodaj 2–3 stacje do Ulubionych i włącz filtr „tylko ulubione”.
4. Włącz Time-shift 3 min.
5. Wybierz zwykłą stację MP3/AAC i odczekaj 10–20 s.
6. Cofnij seekbar o kilka sekund.
7. Kliknij `LIVE`.
8. Powtórz dla kilku bitrate'ów i jednej stacji, która wcześniej miała problemy ze startem.
9. Sprawdź HLS – nie powinien ulec regresji nawet jeśli własny DVR nie jest dostępny.

### Radio – Android Auto

1. Uruchom radio z listy AA.
2. Odczekaj minimum kilka sekund, aż powstanie okno DVR.
3. Sprawdź, czy host pokazuje seekbar (zależy też od UI konkretnego AA).
4. Cofnij pozycję.
5. Przesuń na koniec, aby wrócić do live edge.
6. Upewnij się, że radio nie przełącza samo kolejnej pozycji na live edge.
7. Dodaj/usuń Ulubioną na telefonie i odśwież/otwórz folder Ulubione w AA.

### Eksperymentalne AA Video

1. Najpierw sprawdź stabilne audio/radio w AA z eksperymentem wyłączonym.
2. Ustawienia → Android Auto → włącz eksperymentalny launcher wideo.
3. Ponownie podłącz AA / DHU.
4. Jeżeli host obsługuje CAR_LAUNCHER dla tej kategorii, otwórz eksperymentalną powierzchnię.
5. Sprawdź browse → film → player → powrót.
6. Wyłącz eksperyment i upewnij się, że komponent znika, a stabilny `SmartTubeAutoMusicService` działa nadal.

---

## 9. Kryteria, które uważałbym za blokujące publikację

Nie publikowałbym builda jako stabilnego, jeśli wystąpi którekolwiek z poniższych:

- zmiana ustawienia Odtwarzacza wpływa na AA,
- wybór „Domyślny lektor/napisy” sam przełącza ścieżkę przy starcie,
- radio po błędzie DVR nie wraca do bezpośredniego streamu,
- live edge w AA powoduje automatyczny Next,
- po wyłączeniu eksperymentalnego wideo komponent nadal jest widoczny,
- eksperyment wideo powoduje zmianę/awarię istniejącej MediaSession AA.
