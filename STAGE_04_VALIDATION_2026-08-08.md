# Stage 04 — raport walidacji statycznej

Walidacja wykonana przed spakowaniem paczki.

## Wykonane kontrole

- parsowanie wszystkich XML w `smarttubetv/src/stmobile/res`,
- kontrola duplikatów nazw zasobów w `values` i `values-pl`,
- porównanie kluczy tłumaczeń EN/PL,
- statyczna kontrola wszystkich `R.string`, `R.id`, `R.layout`, `R.drawable`, `R.color`, `R.dimen`, `R.style` użytych w zmienionych plikach Java,
- `javac -XDshould-stop.at=PARSE` dla wszystkich zmienionych plików Java/testowych i filtr błędów parsera (brak Android SDK daje oczekiwane błędy brakujących symboli, ale nie błędy składni),
- kontrola, że nowe funkcje nie dodają hostów/endpointów sieciowych,
- kontrola, że `MobilePlayerPreferences` nie jest używane przez pakiet automotive,
- niezależny smoke-test `MobileTrack + LegacyTrackMapper` na minimalnych stubach (`1920x1080 -> 16:9`, label `1080p60`),
- test zastosowania patcha Stage 04 na czystym Stage 03 i porównanie drzewa wynikowego,
- test integralności finalnego ZIP przez `unzip -t`.

## Celowe ograniczenia

Pełny Android build nie jest uruchamiany w tym środowisku: wrapper wymaga Gradle 7.5, którego dystrybucji brak lokalnie, a Android SDK nie jest dostępny. Kompilację APK wykonuje użytkownik.

## Zakres ryzyka do testu runtime

Najbardziej zależne od urządzenia są:

- czułość pionowych gestów,
- zachowanie producenta telefonu przy zmianie `STREAM_MUSIC`,
- jasność okna na różnych wersjach Androida,
- orientacja/Smart Fit na nietypowych proporcjach ekranów,
- timer przy przejściu aplikacji w tło/PiP,
- interakcja ręcznego panowania powiększonego obrazu z pozostałymi gestami.

## Wyniki bieżącej paczki

- XML: **68/68** poprawnie sparsowanych,
- duplikaty nowych zasobów: **0**,
- brakujące aplikacyjne odwołania `R.*` w zmienionych Java: **0** (**151** sprawdzonych odwołań; 7 dodatkowych trafień to prawidłowe `android.R.string.cancel`),
- błędy składni parsera Java w zmienionych plikach: **0**,
- różnice w pakiecie `automotive`: **0 plików**,
- różnice w pakiecie `nativeui/radio`: **0 plików**,
- nowe hosty/endpointy: **0** (jedyny nowy tekst URL w diff to namespace XML `schemas.android.com`),
- patch: **21** plików zmienionych/dodanych,
- rozszerzony test mapowania video sprawdza `width=1920`, `height=1080` oraz aspect ratio `16:9`; dodatkowy smoke-test mappera zakończył się `mapper-smoke-ok`,
- zastosowanie patcha do czystego Stage 03: **0 różnic** względem drzewa Stage 04.
