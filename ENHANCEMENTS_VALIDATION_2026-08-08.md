# Walidacja – SponsorBlock / DeArrow mobile enhancements (2026-08-08)

## Wykonane kontrole statyczne

- parsowanie wszystkich XML w `smarttubetv/src/stmobile/res`;
- kontrola nowych `R.id` i `R.string` używanych przez zmienione ekrany;
- kontrola duplikatów nazw string resources EN/PL;
- `javac -proc:none` na zmienionych plikach Java – brak diagnostyk parsera/składni (oczekiwane błędy brakujących klas Android/projektu przy braku pełnego classpath);
- osobna kontrola składni `DeArrowService.kt` przy użyciu lokalnego `kotlinc` i minimalnych stubów zależności;
- wyszukanie wszystkich użyć starego `deArrowProcessed` – pozostaje wyłącznie pole kompatybilności `@Deprecated`;
- kontrola, że `SmartTubeAutoMusicService` tworzy provider przez `createForAutomotive(...)`;
- kontrola, że `MobileEnhancementPreferences` nie jest używane w pakiecie automotive;
- porównanie drzewa z `smarttube-aa-features-2026-08-08` – zmiany ograniczone do warstwy SponsorBlock/DeArrow i dokumentacji.

## Pełny build

Pełne `assembleStmobileDebug` nie może zostać wykonane w tym środowisku:

- brak skonfigurowanego Android SDK;
- lokalne Java to OpenJDK 21, podczas gdy projekt/wrapper ma starszy toolchain;
- Gradle wrapper próbuje pobrać Gradle 7.5, ale środowisko przygotowujące paczkę nie ma dostępu do internetu.

Po Twojej stronie uruchom:

```bash
./gradlew :smarttubetv:testStmobileDebugUnitTest
./gradlew :smarttubetv:assembleStmobileDebug
```

Następnie wykonaj checklistę manualną z `ENHANCEMENTS_2026-08-08.md`.
