# Leanback Sweeper — instrukcja bezpiecznego usuwania starej warstwy TV

`leanback-sweeper.py` jest narzędziem migracyjnym przygotowanym dla SmartTube Mobile. Jego zadaniem nie jest mechaniczne skasowanie całego Leanback za jednym razem. Skrypt tworzy **plan migracji**, usuwa tylko elementy uznane za bezpieczne i pozostawia jednoznaczne znaczniki w miejscach wymagających ręcznej przebudowy.

Po zastosowaniu Części 9 skrypt znajduje się w projekcie pod ścieżką:

```text
tools/leanback-sweeper.py
```

W paczce instalacyjnej jest również dostępny jako:

```text
scripts/leanback-sweeper.py
```

## Model bezpieczeństwa

Skrypt działa konserwatywnie:

1. Domyślnie uruchamia się jako **dry-run** i nie modyfikuje projektu.
2. Usuwa wyłącznie jawny import Leanback, którego symbol nie występuje poza komentarzami, literałami tekstowymi i samą sekcją importów.
3. Importy wieloznaczne `*` pozostawia domyślnie do ręcznej oceny.
4. Import nadal używanej klasy pozostawia w pliku i poprzedza komentarzem:

```java
// TODO: Oczyszczone z Leanback [leanback-sweeper]: ...
```

5. Zależność Gradle usuwa w trybie `safe` tylko wtedy, gdy po planowanych zmianach nie pozostają odwołania do odpowiadającej jej rodziny Leanback.
6. Przed zapisem tworzy kopię wszystkich zmienianych plików.
7. Zapis jest atomowy. W razie błędu skrypt przywraca kopię zapasową.
8. Każde uruchomienie generuje raport JSON, raport Markdown i patch `changes.diff`.

## Pierwsze uruchomienie — tylko analiza

Z katalogu głównego projektu:

```powershell
python .\tools\leanback-sweeper.py .
```

Linux/macOS:

```bash
python3 ./tools/leanback-sweeper.py .
```

Raport trafi do:

```text
.leanback-sweeper-reports/<data-i-czas>/
```

Pliki raportu:

```text
report.json
report.md
changes.diff
```

`changes.diff` należy przejrzeć przed użyciem `--apply`.

## Zastosowanie bezpiecznych zmian

```powershell
python .\tools\leanback-sweeper.py . --apply
```

Domyślna polityka to:

```text
--remove-dependencies safe
```

Po zapisie kopia powstaje w:

```text
.leanback-sweeper-backups/<data-i-czas>-<identyfikator>/
```

## Przywrócenie ostatniej kopii

```powershell
python .\tools\leanback-sweeper.py . --restore latest
```

Można też wskazać konkretny katalog:

```powershell
python .\tools\leanback-sweeper.py . --restore .leanback-sweeper-backups\20260727-230000-a1b2c3d4
```

## Zalecana migracja etapowa

Najpierw skanuj wyłącznie nową warstwę mobilną:

```powershell
python .\tools\leanback-sweeper.py . `
  --include smarttubetv/src/stmobile `
  --fail-on-remaining
```

Następnie sprawdź pojedynczy ekran zastępowany przez natywny odpowiednik:

```powershell
python .\tools\leanback-sweeper.py . `
  --include smarttubetv/src/main/java/com/liskovsoft/smartyoutubetv2/tv/ui/browse
```

Dopiero po przekierowaniu launchera, nawigacji i wszystkich zależności danego ekranu uruchom zapis dla tego zakresu:

```powershell
python .\tools\leanback-sweeper.py . `
  --include smarttubetv/src/main/java/com/liskovsoft/smartyoutubetv2/tv/ui/browse `
  --apply
```

Na końcu wykonaj audyt całego repozytorium:

```powershell
python .\tools\leanback-sweeper.py . --fail-on-remaining
```

Kod wyjścia `3` oznacza, że narzędzie znalazło miejsca wymagające dalszej migracji. Jest to przydatne w CI.

## Najważniejsze opcje

| Opcja | Działanie |
|---|---|
| `--apply` | Zapisuje zaplanowane zmiany. Bez tej opcji działa dry-run. |
| `--include PATH` | Ogranicza analizę do ścieżki. Opcję można powtarzać. |
| `--exclude NAME` | Pomija dodatkowy komponent ścieżki. |
| `--remove-dependencies safe` | Usuwa znane zależności tylko po wyeliminowaniu ich użyć. |
| `--remove-dependencies never` | Nigdy nie usuwa zależności Gradle. |
| `--remove-dependencies force` | Wymusza usunięcie znanych zależności. Tryb ryzykowny. |
| `--allow-wildcard-removal` | Pozwala usuwać nieużywane importy `*` po dodatkowej analizie leksykalnej. |
| `--no-remove-imports` | Tworzy wyłącznie raport i komentarze TODO. |
| `--no-annotate` | Nie dodaje komentarzy TODO. |
| `--no-backup` | Wyłącza kopię zapasową. Niezalecane. |
| `--report-dir PATH` | Ustawia własny katalog raportu. |
| `--fail-on-remaining` | Zwraca kod `3`, gdy pozostają zależności do migracji. |
| `--restore latest` | Przywraca ostatni backup. |

## Importy obsługiwane przez skrypt

```text
androidx.leanback.*
com.google.android.exoplayer2.ext.leanback.*
com.google.android.exoplayer.ext.leanback.*
```

Skrypt rozpoznaje:

- importy Java;
- importy statyczne;
- importy Kotlin;
- aliasy Kotlin `as`;
- importy wieloznaczne;
- użycia pełnych nazw klas bez importu.

## Zależności Gradle

Wbudowane bezpieczne reguły obejmują m.in.:

```text
androidx.leanback:leanback
androidx.leanback:leanback-preference
androidx.leanback:leanback-tab
com.google.android.exoplayer:extension-leanback
com.google.android.exoplayer2:extension-leanback
```

Nietypowe deklaracje, zmienne i aliasy katalogu wersji zawierające słowo `leanback` nie są kasowane automatycznie. Są oznaczane do ręcznego sprawdzenia.

## Czego narzędzie celowo nie robi

Skrypt nie:

- zamienia `RowsSupportFragment` na `RecyclerView`;
- przepisuje Presenterów Leanback na adaptery mobilne;
- zmienia logiki fokusu i DPAD;
- usuwa klas z XML;
- usuwa `android.software.leanback` z manifestu;
- gwarantuje, że projekt zbuduje się po ręcznym użyciu trybu `force`;
- rozwiązuje kolizji symboli pochodzących z kilku importów o tej samej nazwie.

Te operacje wymagają świadomej migracji ekranu. Sweeper wskazuje miejsca, ale nie wymyśla automatycznie nowej architektury.

## Procedura przed zatwierdzeniem zmian

Po `--apply` wykonaj:

```powershell
.\gradlew.bat :smarttubetv:testStmobileDebugUnitTest --stacktrace
.\gradlew.bat :smarttubetv:assembleStmobileDebug --stacktrace
```

Następnie wyszukaj pozostałe znaczniki:

```powershell
Get-ChildItem -Recurse -Include *.java,*.kt,*.gradle,*.kts |
  Select-String "TODO: Oczyszczone z Leanback"
```

Dopiero po przejściu kompilacji i testów można usunąć katalog backupu.
