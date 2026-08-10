# SponsorBlock / DeArrow – integracja z nowym mobile UI (2026-08-08)

Ta paczka jest **kolejną warstwą nad `smarttube-aa-features-2026-08-08`**. Nie usuwa wcześniejszych poprawek ani funkcji. Celem tej warstwy jest dokończenie integracji SponsorBlock/DeArrow z nowym natywnym interfejsem telefonu/tabletu, bez zmiany stabilnej ścieżki Android Auto.

## 1. Nowe opcje – domyślnie włączone

W `Ustawienia → Odtwarzacz → Integracje mobilne` dodano cztery niezależne przełączniki. Wszystkie domyślnie mają wartość `ON`:

1. **Kolorowe znaczniki SponsorBlock / rozdziałów na osi czasu**
   - włącza renderowanie segmentów dostarczanych przez istniejący `PlaybackPresenter` na nowym mobilnym seekbarze;
   - nie zmienia logiki automatycznego pomijania SponsorBlock;
   - nadal respektuje globalne ustawienia SponsorBlock (w tym globalne włączenie/wyłączenie znaczników).

2. **Tytuły i miniatury DeArrow na nowych listach mobilnych**
   - podłącza istniejące opcje społecznościowych tytułów i miniaturek do Home/Search/Channel/playlist w nowym mobile UI;
   - nadal respektuje istniejące globalne przełączniki DeArrow: osobno tytuły i osobno miniatury;
   - wyłączenie tego mobilnego przełącznika nie zmienia zachowania starego TV UI.

3. **Oryginalne/niezlokalizowane tytuły na nowych listach mobilnych**
   - podłącza istniejącą opcję oryginalnych tytułów (oEmbed) do natywnych list mobilnych;
   - działa tylko, gdy istniejąca globalna opcja „Oryginalne tytuły materiałów wideo” jest aktywna;
   - tytuł DeArrow ma pierwszeństwo, jeśli w starych preferencjach oba tryby byłyby jednocześnie aktywne.

4. **Zapasowa klatka miniatury YouTube (początek/środek/koniec)**
   - przenosi istniejące ustawienie `hq1/hq2/hq3` ze starego `VideoCardPresenter` także do natywnych list mobilnych;
   - jest używane tylko jako fallback, gdy dla filmu nie zastosowano miniatury DeArrow;
   - live/upcoming i istniejące alternatywne miniatury zachowują dotychczasowe priorytety.

Wszystkie cztery przełączniki są zapisane w osobnym pliku `SharedPreferences`: `smarttube_mobile_enhancements`. Android Auto go nie odczytuje.

> Ważne: mobilne przełączniki są dodatkową bramką. Nie wymuszają zmiany globalnych ustawień SponsorBlock/DeArrow. Dzięki temu istniejąca konfiguracja użytkownika nie jest nadpisywana po aktualizacji.

## 2. SponsorBlock – kolorowe segmenty na nowym seekbarze

Wcześniej `LegacyMobilePlaybackRepository.setSeekBarSegments(...)` był pustą metodą, więc wspólny `SponsorBlockController` mógł pomijać segmenty, ale nowy mobilny player ich nie rysował.

Zmiany:

- `LegacyMobilePlaybackRepository` zapisuje kopię `SeekBarSegment` i emituje ją w `MobilePlaybackSnapshot`;
- `MobilePlaybackSnapshot` przechowuje niemutowalną kopię segmentów;
- nowy `MobileSegmentSeekBar` rysuje kolorowe zakresy nad standardowym paskiem postępu i ponownie rysuje thumb na wierzchu;
- `MobilePlaybackFragment` pokazuje segmenty tylko, gdy mobilny przełącznik jest aktywny i odtwarzany materiał nie jest radiem.

Nie zmieniono algorytmu SponsorBlock, pobierania segmentów ani logiki seek. To jest wyłącznie brakująca warstwa prezentacji dla nowego playera.

## 3. DeArrow / oryginalne tytuły – natywne listy mobile

Nowe repozytoria mobilne (`LegacyBrowseRepository`, `LegacySearchRepository`, `LegacyChannelRepository`) mapowały `MediaGroup` bez przejścia przez legacy `BrowseProcessorManager`. W efekcie stare ustawienia DeArrow były widoczne w ustawieniach, ale nie zmieniały kart w nowym mobile UI.

Dodano `MobileMetadataEnhancer`:

```text
YouTube MediaGroup
      │
      ├── natychmiastowe mapowanie → UI pokazuje oryginalne dane YouTube
      │
      └── background metadata pass
              ├── DeArrow branding (jeżeli aktywny)
              └── original/unlocalized title (jeżeli aktywny)
                       │
                       ↓
              cache po videoId
                       │
                       ↓
              ponowne mapowanie → dyskretna aktualizacja karty
```

Dzięki temu listy nie czekają na zewnętrzne API. Użytkownik najpierw dostaje normalną listę YouTube, a karta jest podmieniana dopiero, gdy opcjonalne metadane są gotowe.

`VideoGroup.from(MediaGroup)` tworzy nowe wrappery `Video` podczas kolejnych mapowań. Dlatego dane DeArrow/original title nie są przechowywane wyłącznie w chwilowym obiekcie `Video`; `MobileMetadataEnhancer` trzyma niewielki cache po `videoId`, a `LegacyMediaMapper` stosuje go za każdym mapowaniem nowej karty.

## 4. Wydajność – cache i kontrolowana równoległość

### DeArrow service cache

`DeArrowService` ma teraz procesowy, ograniczony cache LRU:

- maksymalnie **512** wpisów;
- wynik pozytywny: **6 h**;
- brak wyniku: **2 min**;
- zapisywane są tylko: `videoId`, alternatywny tytuł i URL miniatury;
- `clearCache()` umożliwia jawne wyczyszczenie pamięci.

Nie jest to cache dyskowy i po ubiciu procesu znika.

### Równoległość

`YouTubeMediaItemService.getDeArrowDataObserve(List<String>)` przestał wykonywać wszystkie zapytania sekwencyjnie. Maksymalnie **4** elementy są pobierane jednocześnie. Błąd pojedynczego filmu nie przerywa przetwarzania pozostałych kart.

Oryginalne tytuły również są pobierane z maksymalną równoległością 4, a wyniki są przechowywane w procesowym cache nowej warstwy mobilnej.

### Ograniczenie pamięci

Cache metadanych mobilnych jest ograniczony do około **2048 identyfikatorów** i jest resetowany po osiągnięciu limitu. Osobno ograniczono zbiór negatywnych/już sprawdzonych oryginalnych tytułów.

## 5. Naprawiony konflikt `deArrowProcessed`

Stary `DeArrowProcessor` i `UnlocalizedTitleProcessor` używały tego samego pola `Video.deArrowProcessed`. Powodowało to sytuację, w której jeden procesor mógł oznaczyć film jako obsłużony i przypadkowo zablokować drugi.

Dodano dwa niezależne znaczniki:

- `deArrowBrandingProcessed`;
- `unlocalizedTitleProcessed`.

Stare `deArrowProcessed` pozostaje tylko jako pole `@Deprecated`, żeby nie ryzykować niepotrzebnego zerwania kompatybilności serializacji/starych odwołań.

Ta poprawka dotyczy także starej warstwy TV i usuwa realny konflikt istniejących opcji.

## 6. Browse/Search/Channel/Playlist

Integracja obejmuje:

- Home i pozostałe strony Browse;
- kolejne strony / continuation;
- Shorts po odświeżeniu continuation;
- Search;
- Channel;
- pierwszy ekran playlisty;
- pełną playlistę doładowywaną w tle;
- prefetch Browse.

W Browse i playlistach metadane są dogrzewane niezależnie od cyklu życia ekranu: jeśli użytkownik szybko wyjdzie, późny wynik może nadal poprawić cache, ale nie wysyła callbacku do już anulowanego ekranu. Zapobiega to sytuacji, w której anulowanie ekranu pozostawia na 10–30 minut cache, którego nigdy nie uda się wzbogacić.

Pełna playlista jest obecnie składana najpierw z surowych `MediaGroup`, a następnie cały zestaw stron przechodzi przez enhancement. Background loader nie nadpisuje już wzbogaconej pierwszej strony surowymi tytułami/miniaturami.

## 7. Android Auto – izolacja

Dodano dwa sposoby utworzenia providera:

```java
SmartTubeMobileNativeProvider.create(context)              // mobile UI + enhancery
SmartTubeMobileNativeProvider.createForAutomotive(context) // stabilne AA bez enhancerów kart
```

`SmartTubeAutoMusicService` używa `createForAutomotive(...)`.

To oznacza:

- mobilne przełączniki DeArrow/SponsorBlock UI nie są czytane przez stable AA;
- nowe pobieranie DeArrow dla kart nie jest dokładane do browse Android Auto;
- istniejący playback AA pozostaje na osobnym `automotivePlaybackRepository()`;
- wcześniejszy eksperymentalny AA Video nadal jest osobnym komponentem i **pozostaje domyślnie wyłączony** jako celowy wyjątek bezpieczeństwa.

SponsorBlock może nadal wykonywać istniejące działania playbackowe w AA, jeśli robił to wcześniej; ta paczka nie zmienia tego zachowania. Nowy mobilny switch kontroluje tylko rysowanie segmentów w telefonowym seekbarze.

## 8. Domyślne wartości i reset

`MobileEnhancementPreferences`:

```text
SponsorBlock seekbar markers     ON
DeArrow native lists             ON
Original titles native lists     ON
Fallback thumbnail frame         ON
```

Przycisk `Przywróć ustawienia odtwarzacza` czyści również te preferencje, więc po resecie wracają do wartości domyślnych `ON`.

Wyjątek z wcześniejszego planu: **Eksperymentalne AA Video = OFF domyślnie**. Jest to funkcja eksperymentalna zależna od środowiska samochodu i ograniczeń Android Auto, dlatego nie jest automatycznie aktywowana po instalacji/aktualizacji.

## 9. Najważniejsze pliki

```text
MediaServiceCore/youtubeapi/.../dearrow/DeArrowService.kt
MediaServiceCore/youtubeapi/.../service/YouTubeMediaItemService.java
common/.../models/data/Video.java
common/.../misc/DeArrowProcessor.java
common/.../misc/UnlocalizedTitleProcessor.java

smarttubetv/src/stmobile/.../legacy/MobileMetadataEnhancer.java
smarttubetv/src/stmobile/.../legacy/LegacyMediaMapper.java
smarttubetv/src/stmobile/.../legacy/LegacyBrowseRepository.java
smarttubetv/src/stmobile/.../legacy/LegacySearchRepository.java
smarttubetv/src/stmobile/.../legacy/LegacyChannelRepository.java
smarttubetv/src/stmobile/.../legacy/SmartTubeMobileNativeProvider.java
smarttubetv/src/stmobile/.../legacy/LegacyMobilePlaybackRepository.java

smarttubetv/src/stmobile/.../player/MobileEnhancementPreferences.java
smarttubetv/src/stmobile/.../player/MobileSegmentSeekBar.java
smarttubetv/src/stmobile/.../fragment/MobilePlaybackFragment.java
smarttubetv/src/stmobile/.../fragment/PlayerSettingsFragment.java
```

## 10. Testy manualne po kompilacji

### SponsorBlock

1. Włącz SponsorBlock i globalne kolorowe znaczniki.
2. Otwórz film posiadający segmenty SponsorBlock.
3. Sprawdź, czy kolorowe zakresy są widoczne na osi czasu.
4. Wyłącz wyłącznie `Ustawienia → Odtwarzacz → Kolorowe znaczniki...`.
5. Otwórz/odśwież player – auto-skip powinien nadal działać, ale zakresy nie powinny być rysowane.

### DeArrow

1. Włącz globalnie community titles oraz mobilne `DeArrow native lists`.
2. Otwórz Home, Search i Channel.
3. Lista powinna pojawić się natychmiast; po odpowiedzi API część kart może zmienić tytuł.
4. Wyłącz mobilny gate – native mobile powinien wrócić do tytułów YouTube przy kolejnym mapowaniu/odświeżeniu, bez zmiany starego UI.
5. Powtórz test dla community thumbnails.

### Original titles

1. Włącz globalne `Oryginalne tytuły materiałów wideo`.
2. Pozostaw mobilny gate ON.
3. Sprawdź Home/Search/Channel.
4. Wyłącz mobilny gate i potwierdź powrót do zwykłych tytułów w native mobile.

### Fallback thumbnails

1. Ustaw w DeArrow `Zapasowe źródło miniatur` na początek/środek/koniec.
2. Pozostaw community thumbnail wyłączoną lub wybierz film bez DeArrow thumbnail.
3. Potwierdź zmianę klatki w native mobile.
4. Wyłącz mobilny fallback gate – native mobile powinien wrócić do zwykłej miniatury.

### Android Auto

1. Uruchom stabilne AA radio/playback.
2. Zmieniaj wszystkie cztery mobilne przełączniki.
3. Stable AA browse/playback nie powinien zmieniać zachowania z powodu tych ustawień.

## 11. Co pozostaje niezmienione

Paczka powstała przez skopiowanie kompletnego `smarttube-aa-features-2026-08-08` i nałożenie powyższej warstwy. Zachowane są m.in.:

- naprawa continuation/infinite scroll Shorts;
- naprawa rozjeżdżającego się dolnego paska/seekbara;
- naprawa tap vs pan po zoomie;
- globalne radio + search + favorites;
- Trending oddzielone od Home;
- obsługa przejściowych 403 bez fałszywego błędu UI;
- Ustawienia Odtwarzacza, preferowany audio/subtitle i nowy picker;
- Radio DVR/time-shift + Android Auto seek;
- eksperymentalne parked AA Video jako osobny, opt-in komponent.

Szczegóły tych wcześniejszych elementów: `FIXES_2026-08-08.md` i `FEATURES_INTEGRATION_2026-08-08.md`.
