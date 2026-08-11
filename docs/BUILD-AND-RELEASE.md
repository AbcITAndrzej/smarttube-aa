# Budowanie i wydawanie dwoch wariantow

Glownym narzedziem jest przenosny plik `SMARTUBE-GITHUB-HELPER-v1.bat` w katalogu projektu.
Helper zawsze pracuje wzgledem folderu, w ktorym sam sie znajduje.

## Najwazniejsze opcje

- `9` - buduje Music oraz Video EXP, kazde jako arm64 i universal.
- `10` - tworzy lokalny katalog wydania z obydwoma wariantami, zrodlami i SHA256.
- `11` - publikuje jeden GitHub Release zawierajacy obie aplikacje.
- `12` - wykonuje lokalna kopie Git oraz dolacza znalezione APK obu wariantow.

## Polecenia bez menu

```bat
SMARTUBE-GITHUB-HELPER-v1.bat --self-test
SMARTUBE-GITHUB-HELPER-v1.bat --status
SMARTUBE-GITHUB-HELPER-v1.bat --build
SMARTUBE-GITHUB-HELPER-v1.bat --package
```

## Zasady bezpieczenstwa wydania

- Publikacja wymaga czystego drzewa Git.
- Helper sprawdza, czy wszystkie APK pochodza z aktualnego numeru wersji.
- `update.json` pozostaje kanalem automatycznych aktualizacji tylko dla Music.
- Video EXP jest obecnie aktualizowane recznie z GitHub Releases.
- Nie nalezy publikowac pozostalosci wariantu Hybrid ani prototypu szybkiego przelaczania.
