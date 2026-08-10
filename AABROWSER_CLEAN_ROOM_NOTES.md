# AABrowser – notatki clean-room

Źródło referencyjne: https://github.com/kododake/AABrowser

## Zasada użyta w tym pakiecie

Nie kopiowano plików Java/Kotlin/XML ani fragmentów implementacji AABrowser. Integracja samochodowego wideo została napisana niezależnie na bazie publicznie widocznego zachowania platformy i oficjalnej koncepcji parked apps.

Powody:

1. stabilna usługa Android Auto audio/radio w SmartTube ma pozostać nienaruszona,
2. AABrowser stosuje deklaracje manifestu, których nie chcemy przenosić jako obejście ograniczeń hosta,
3. repozytorium AABrowser jest udostępniane na GPLv3, więc bezpośrednie kopiowanie kodu do drzewa o innej licencji byłoby niepotrzebnym ryzykiem licencyjnym.

## Co zrobiono zamiast tego

- osobna `ExperimentalCarVideoActivity`,
- `android:enabled="false"` domyślnie,
- jawny opt-in w ekranie ustawień Android Auto,
- `PackageManager` włącza/wyłącza tylko eksperymentalny komponent,
- deklaracja `CAR_LAUNCHER`,
- brak `appCategory="game"`, `NAVIGATION`, `APP_MAPS`, `distractionOptimized=true`,
- stabilny `SmartTubeAutoMusicService` pozostaje osobnym komponentem i nie zależy od eksperymentu.

## Linki kontrolne

- AABrowser manifest: https://github.com/kododake/AABrowser/blob/main/app/src/main/AndroidManifest.xml
- AABrowser LICENSE: https://github.com/kododake/AABrowser/blob/main/LICENSE
- Android parked apps: https://developer.android.com/training/cars/parked/auto

## Stan platformy przy przygotowaniu paczki

Oficjalna dokumentacja Android Auto z 24.06.2026 opisuje `CAR_LAUNCHER` dla parked apps, ale jednocześnie zaznacza, że **obecnie jedyną wspieraną kategorią parked apps w Android Auto są gry**. Dlatego eksperymentalna aktywność wideo w tej paczce jest przygotowana architektonicznie i izolowana od stabilnego AA, lecz na produkcyjnym Android Auto może nie pojawić się w launcherze do czasu oficjalnego udostępnienia kategorii wideo. Nie obchodzimy tego przez deklarowanie aplikacji jako gry/nawigacji.
