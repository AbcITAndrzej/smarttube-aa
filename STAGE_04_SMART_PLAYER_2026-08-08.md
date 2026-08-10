# Stage 04 — Smart Player UX

Baza: `smarttube-aa-stage-03-pagination-2026-08-08`

Ten etap rozwija wyłącznie nowy mobilny player. Nie zmienia stabilnego `SmartTubeAutoMusicService`, logiki Android Auto ani Radio DVR. Wszystkie funkcje Stage 04 mają ustawienia użytkownika i dodatkowo wspólną awaryjną bramkę `smart_player_ux` w Diagnostyce.

## Co dodano

### 1. Gest jasności

- pionowy ruch po lewej połowie obrazu reguluje jasność okna playera,
- działa tylko dla zwykłego VOD; Shorts zachowują pionową nawigację, a Radio swoje sterowanie,
- pokazuje lokalny overlay z procentem,
- nie wymaga uprawnienia do modyfikacji globalnej jasności systemowej,
- po opuszczeniu playera przywracany jest poprzedni `WindowManager.LayoutParams.screenBrightness`, więc player nie zostawia wymuszonej jasności w pozostałej części aplikacji,
- przełącznik: `Odtwarzacz -> Gest jasności`.

### 2. Gest głośności

- pionowy ruch po prawej połowie obrazu reguluje `AudioManager.STREAM_MUSIC`,
- pokazuje procent głośności,
- działa tylko w zwykłym VOD, aby nie kolidować z Shorts i Radiem,
- przełącznik: `Odtwarzacz -> Gest głośności`.

### 3. Konfigurowalne double-tap

Dotychczasowe ±10 s zostało zachowane jako domyślne, ale użytkownik może wybrać:

- 5 s,
- 10 s,
- 15 s,
- 30 s.

Sam mechanizm double-tap nadal ma osobny przełącznik. Wyłączenie awaryjnej bramki Stage 04 przywraca legacy ±10 s bez kasowania ustawionej wartości.

### 4. Blokada dotyku

- nowy przycisk kłódki w górnym pasku playera,
- po zablokowaniu wszystkie gesty i zwykłe kontrolki są ignorowane,
- pojedyncze stuknięcie pokazuje tylko przycisk `Odblokuj`, który po 2,5 s ponownie znika,
- przycisk Wstecz podczas blokady najpierw odblokowuje zamiast zamykać player,
- player nigdy nie uruchamia się automatycznie w stanie zablokowanym,
- przełącznik funkcji w ustawieniach.

### 5. Timer uśpienia

Dostępny przez `Więcej -> Timer uśpienia`:

- wyłączony,
- 15 min,
- 30 min,
- 45 min,
- 60 min,
- do końca bieżącego filmu.

Timery minutowe używają `SystemClock.elapsedRealtime()`, więc nie zależą od zmiany czasu zegara. Tryb `do końca bieżącego filmu` jest związany z ID aktualnego materiału i stanem `ENDED`/przejściem do następnej pozycji, zamiast zgadywać koniec z zegara ściennego. Po zakończeniu wywoływane jest `pause()`, a nie niszczenie playera. Aktywny timer jest pokazywany jako mała etykieta; dla timera czasowego pokazuje pozostałe minuty, a dla końca filmu — stan `do końca filmu`. Stan timera jest zachowywany przez zmianę konfiguracji/rotację fragmentu.

Timer jest funkcją opcjonalną; samo włączenie jej w ustawieniach nie uruchamia żadnego odliczania.

### 6. Zapamiętywanie pinch-zoom

- skala 1.0–4.0 jest zapisywana po zakończeniu gestu,
- osobny stan dla zwykłego VOD i Shorts,
- translacja/pan nie jest zapisywana celowo, aby nowy film zawsze zaczynał się wycentrowany,
- wyłączenie opcji natychmiast powoduje używanie skali 1.0 dla kolejnych materiałów, bez kasowania zapisanej preferencji.

### 7. Smart Fit

`MobileTrack` przenosi teraz rzeczywiste `width/height` formatu video. Player porównuje proporcje video z proporcjami powierzchni odtwarzania:

- gdy różnica proporcji wynosi maks. 20%, wybiera `RESIZE_MODE_ZOOM`,
- przy większej różnicy wybiera bezpieczny `RESIZE_MODE_FIT`,
- `FILL` nigdy nie jest wybierany automatycznie,
- ręczne naciśnięcie przycisku dopasowania ustawia override dla bieżącego filmu,
- override resetuje się przy następnym materiale.

Dzięki temu automatyka nie walczy z użytkownikiem po ręcznej zmianie sposobu dopasowania.

## Ustawienia

W `Ustawienia -> Odtwarzacz` dodano:

- gest jasności,
- gest głośności,
- wybór interwału double-tap,
- blokadę dotyku,
- timer uśpienia,
- zapamiętywanie zoomu,
- Smart Fit.

Wszystkie możliwości są domyślnie dostępne, ale blokada i timer nie aktywują się same.

## FeatureFlag / rollback

W `Ustawienia -> Diagnostyka` dodano:

`Stage 4: Smart Player UX`

Wyłączenie ustawia `MobileFeatureFlags.SMART_PLAYER_UX=false` i omija nową logikę Stage 04 bez usuwania kodu i bez kasowania preferencji użytkownika.

## Diagnostyka

Raport zawiera teraz wpis `SmartPlayerUX` z wartościami:

- master flag,
- brightness gesture,
- volume gesture,
- double-tap seconds,
- lock,
- sleep timer,
- remember zoom,
- smart fit.

## Izolacja

Stage 04 nie dodaje żadnego nowego połączenia sieciowego i nie odczytuje nowych danych z API. Nie zmienia:

- `SmartTubeAutoMusicService`,
- Android Auto preferences,
- Radio Browser,
- Radio DVR/time-shift,
- SponsorBlock/DeArrow network stack,
- Instant Play recovery.

## Pliki zmienione / dodane

Najważniejsze:

- `MobilePlaybackFragment.java`
- `MobilePlayerPreferences.java`
- `PlayerSettingsFragment.java`
- `MobilePlaybackViewModel.java`
- `MobileTrack.java`
- `LegacyTrackMapper.java`
- `MobileFeatureFlags.java`
- `MobileDiagnosticsStore.java`
- `DiagnosticsFragment.java`
- `mobile_native_fragment_playback.xml`
- `mobile_player_settings_fragment.xml`
- `mobile_diagnostics_fragment.xml`
- nowe drawables lock/unlock/feedback,
- nowe zasoby PL/EN,
- rozszerzony test `LegacyTrackMapperTest` sprawdzający przekazanie geometrii 1920x1080 i aspect ratio do `MobileTrack`.

## Checklista testów na urządzeniu

1. Zwykły VOD: pionowo lewa połowa -> jasność; po wyjściu jasność aplikacji wraca do poprzedniej.
2. Zwykły VOD: pionowo prawa połowa -> głośność systemowego strumienia muzyki.
3. Shorts: pionowy swipe nadal przełącza shorty i nie uruchamia jasności/głośności.
4. Radio: gesty Stage 04 nie ingerują w Radio DVR.
5. Double-tap dla 5/10/15/30 s w lewo i prawo.
6. Wyłączenie double-tap -> brak seek po podwójnym stuknięciu.
7. Kłódka -> kontrolki i gesty nie działają; tap pokazuje `Odblokuj`; Back odblokowuje.
8. Timer 15 min można skrócić testowo przez debugger lub sprawdzić `Do końca filmu`; po końcu player przechodzi w pauzę.
9. Obrót ekranu z aktywnym timerem -> pozostały czas nie resetuje się.
10. Zoom VOD -> następny VOD dziedziczy skalę; Shorty mają osobną skalę.
11. Wyłączenie `Zapamiętuj zoom` -> następny materiał startuje 1.0x.
12. Smart Fit: 16:9 na ekranie o zbliżonych proporcjach -> ZOOM; pionowe/znacznie inne proporcje -> FIT.
13. Ręczna zmiana FIT/ZOOM/FILL -> Smart Fit nie nadpisuje jej w tym samym filmie.
14. Następny film -> Smart Fit ponownie przejmuje wybór.
15. Diagnostyka -> wyłączenie `Stage 4 Smart Player UX` wyłącza nowe zachowania bez wpływu na legacy player/AA.
16. Ponownie sprawdzić stare regresje: zoom+tap chowa kontrolki, zmiana jakości nie rozjeżdża seekbara, Shorts continuation działa, 403 recovery działa.
