@echo off
setlocal EnableExtensions EnableDelayedExpansion
chcp 65001 >nul
title SmartTube AA - GitHub Helper Portable

set "SCRIPT_DIR=%~dp0"
set "HELPER_NAME=SMARTUBE-GITHUB-HELPER-v1.bat"
set "DEFAULT_REPO=AbcITAndrzej/smarttube-aa"
set "BRANCH=main"
set "PROJECT_DIR=%SCRIPT_DIR:~0,-1%"
set "SOURCE_DIR=%PROJECT_DIR%"
set "PUBLIC_DIR=%PROJECT_DIR%"
set "WORKSPACE_DIR=%PROJECT_DIR%"
set "RELEASES_DIR=%PROJECT_DIR%\releases"
set "CONFIG_DIR=%RELEASES_DIR%\helper-config"
set "CONFIG_FILE=%CONFIG_DIR%\config.txt"
set "APK_STORE=%RELEASES_DIR%\apk-cache"
set "BUILD_APK_DIR=%SOURCE_DIR%\smarttubetv\build\outputs\apk\stmobile\debug"
set "REPO=%DEFAULT_REPO%"

if exist "%CONFIG_FILE%" (
  for /f "usebackq tokens=1,* delims==" %%A in ("%CONFIG_FILE%") do (
    if /I "%%A"=="REPO" set "REPO=%%B"
    if /I "%%A"=="BRANCH" set "BRANCH=%%B"
  )
)

if /I "%~1"=="--self-test" goto SELF_TEST
if /I "%~1"=="--status" goto STATUS_ONCE
if /I "%~1"=="--prepare" goto PREPARE_ONCE
if /I "%~1"=="--build" goto BUILD_ONCE
if /I "%~1"=="--package" goto PACKAGE_ONCE
if /I "%~1"=="--version" goto VERSION_ONCE
if /I "%~1"=="--manifest" goto MANIFEST_ONCE
if /I "%~1"=="--init-local" goto INIT_ONCE

:MENU
cls
echo ============================================================
echo       SMARTTUBE AA - GITHUB HELPER PORTABLE
echo ============================================================
echo Projekt            : %PROJECT_DIR%
echo GitHub             : %REPO%
echo Branch             : %BRANCH%
echo Paczki lokalne     : %RELEASES_DIR%
echo ============================================================
echo.
echo  1 - Status i kontrola projektu
echo  2 - Sprawdz przenosny katalog projektu
echo  3 - Zainicjuj Git w tym projekcie
echo  4 - Ustaw repo GitHub i remote origin
echo  5 - Zaloguj/sprawdz GitHub CLI
echo  6 - Utworz PUBLICZNE repo GitHub i wykonaj pierwszy push
echo  7 - Synchronizuj + commit + push
echo  8 - Pull --ff-only z GitHub
echo  9 - Zbuduj APK lokalnie
echo 10 - Utworz lokalna paczke release
echo 11 - Opublikuj GitHub Release z APK i ZIP zrodel
echo 12 - Pelna lokalna kopia Git: bundle + snapshot + patche
echo 13 - Pokaz/pobierz najnowszy GitHub Release
echo 14 - Otworz repo lub katalog publiczny
echo  0 - Wyjscie
echo.
set "OPT="
set /p "OPT=Wybierz opcje: "
if "%OPT%"=="1" goto STATUS
if "%OPT%"=="2" goto PREPARE
if "%OPT%"=="3" goto INIT_GIT
if "%OPT%"=="4" goto CONFIGURE_REPO
if "%OPT%"=="5" goto GH_LOGIN
if "%OPT%"=="6" goto CREATE_PUBLIC_REPO
if "%OPT%"=="7" goto COMMIT_PUSH
if "%OPT%"=="8" goto PULL
if "%OPT%"=="9" goto BUILD_APK
if "%OPT%"=="10" goto LOCAL_PACKAGE
if "%OPT%"=="11" goto PUBLISH_RELEASE
if "%OPT%"=="12" goto FULL_BACKUP
if "%OPT%"=="13" goto RELEASE_INFO
if "%OPT%"=="14" goto OPEN_TARGET
if "%OPT%"=="0" exit /b 0
goto MENU

:SELF_TEST
echo [SELF-TEST] SmartTube GitHub Helper Portable
call :CHECK_SOURCE
if errorlevel 1 exit /b 1
call :CHECK_GIT_TOOL
if errorlevel 1 exit /b 1
call :GET_VERSION
if errorlevel 1 exit /b 1
echo [OK] PROJECT_DIR=%PROJECT_DIR%
echo [OK] REPO=%REPO%
echo [OK] BRANCH=%BRANCH%
echo [OK] APP_VERSION=!APP_VERSION! ^(versionCode !APP_VERSION_CODE!^)
if exist "%BUILD_APK_DIR%" (
  echo [OK] Katalog wynikow APK istnieje.
) else (
  echo [INFO] Katalog APK powstanie po buildzie.
)
exit /b 0

:STATUS_ONCE
call :SHOW_STATUS
exit /b %errorlevel%

:PREPARE_ONCE
call :SYNC_PUBLIC_COPY
exit /b %errorlevel%

:BUILD_ONCE
call :RUN_LOCAL_BUILD
exit /b %errorlevel%

:PACKAGE_ONCE
call :CREATE_RELEASE_PACKAGE
exit /b %errorlevel%

:VERSION_ONCE
call :GET_VERSION
if errorlevel 1 exit /b 1
echo APP_VERSION=!APP_VERSION!
echo APP_VERSION_CODE=!APP_VERSION_CODE!
exit /b 0

:MANIFEST_ONCE
call :WRITE_UPDATE_MANIFEST "%PUBLIC_DIR%\update.json"
exit /b %errorlevel%

:INIT_ONCE
call :INIT_LOCAL_REPO
exit /b %errorlevel%

:STATUS
cls
call :SHOW_STATUS
echo.
pause
goto MENU

:SHOW_STATUS
call :CHECK_SOURCE
if errorlevel 1 exit /b 1
echo ============================================================
echo Projekt          : %PROJECT_DIR%
echo Repo GitHub      : %REPO%
echo Branch           : %BRANCH%
echo ============================================================
if exist "%PUBLIC_DIR%\.git" (
  echo.
  git -C "%PUBLIC_DIR%" status --short --branch
  echo.
  git -C "%PUBLIC_DIR%" remote -v
) else (
  echo [INFO] Projekt nie ma jeszcze Git. Uzyj opcji 3.
)
echo.
call :FIND_LATEST_APK
if defined LATEST_APK (
  echo APK arm64    : !LATEST_APK!
) else (
  echo [INFO] Brak gotowego APK arm64. Uzyj opcji 9.
)
if defined LATEST_UNIVERSAL_APK (
  echo APK universal: !LATEST_UNIVERSAL_APK!
) else (
  echo [INFO] Brak gotowego APK universal. Uzyj opcji 9.
)
exit /b 0

:PREPARE
cls
call :SYNC_PUBLIC_COPY
if errorlevel 1 goto COMMAND_FAILED
echo.
echo [OK] Przenosny katalog projektu jest gotowy:
echo %PROJECT_DIR%
echo.
pause
goto MENU

:SYNC_PUBLIC_COPY
call :CHECK_SOURCE
if errorlevel 1 exit /b 1
echo [OK] Helper pracuje bezposrednio w swoim katalogu:
echo %PROJECT_DIR%
echo [INFO] Po przeniesieniu calego folderu uruchom %HELPER_NAME% z jego nowej lokalizacji.
exit /b 0

:INIT_GIT
cls
call :INIT_LOCAL_REPO
if errorlevel 1 goto COMMAND_FAILED
echo.
git -C "%PUBLIC_DIR%" status --short --branch
echo.
pause
goto MENU

:INIT_LOCAL_REPO
call :SYNC_PUBLIC_COPY
if errorlevel 1 exit /b 1
if exist "%PUBLIC_DIR%\.git" (
  echo [INFO] Git jest juz zainicjowany.
) else (
  git -C "%PUBLIC_DIR%" init
  if errorlevel 1 exit /b 1
  git -C "%PUBLIC_DIR%" branch -M "%BRANCH%"
  echo Tworze pierwszy lokalny commit kopii publicznej...
  call :ENSURE_INITIAL_COMMIT
  if errorlevel 1 exit /b 1
  echo [OK] Lokalne repo Git i pierwszy commit sa gotowe.
  echo Nic nie zostalo wyslane do internetu.
)
exit /b 0

:CONFIGURE_REPO
cls
echo Aktualne repo: %REPO%
echo Format: wlasciciel/nazwa, np. AbcITAndrzej/smarttube-aa
echo.
set "NEW_REPO="
set /p "NEW_REPO=Nowe repo [Enter = bez zmian]: "
if defined NEW_REPO set "REPO=!NEW_REPO!"
set "NEW_BRANCH="
set /p "NEW_BRANCH=Branch [%BRANCH%]: "
if defined NEW_BRANCH set "BRANCH=!NEW_BRANCH!"
if not exist "%CONFIG_DIR%" mkdir "%CONFIG_DIR%"
(
  echo REPO=!REPO!
  echo BRANCH=!BRANCH!
) > "%CONFIG_FILE%"
if exist "%PUBLIC_DIR%\.git" (
  git -C "%PUBLIC_DIR%" remote remove origin >nul 2>nul
  git -C "%PUBLIC_DIR%" remote add origin "https://github.com/!REPO!.git"
  git -C "%PUBLIC_DIR%" branch -M "!BRANCH!"
)
echo.
echo [OK] Zapisano konfiguracje: !REPO! branch !BRANCH!
echo.
pause
goto MENU

:GH_LOGIN
cls
call :CHECK_GH_TOOL
if errorlevel 1 (
  echo Mozesz zainstalowac GitHub CLI poleceniem:
  echo winget install --id GitHub.cli
  pause
  goto MENU
)
gh auth status --hostname github.com
if errorlevel 1 (
  echo.
  echo Otwieram logowanie GitHub w przegladarce...
  gh auth login --hostname github.com --git-protocol https --web
  if errorlevel 1 goto COMMAND_FAILED
)
gh auth setup-git
echo.
gh auth status --hostname github.com
echo.
pause
goto MENU

:CREATE_PUBLIC_REPO
cls
call :REQUIRE_GIT_REPO
if errorlevel 1 goto COMMAND_FAILED
call :ENSURE_GH_AUTH
if errorlevel 1 goto COMMAND_FAILED
echo Utworzone zostanie PUBLICZNE repo:
echo https://github.com/%REPO%
echo.
echo Zrodla sa oparte na SmartTube i zachowuja oryginalna licencje/atrybucje.
set "CONFIRM_PUBLIC="
set /p "CONFIRM_PUBLIC=Wpisz PUBLIC aby utworzyc repo i wykonac pierwszy push: "
if /I not "!CONFIRM_PUBLIC!"=="PUBLIC" (
  echo Anulowano.
  pause
  goto MENU
)
gh repo view "%REPO%" >nul 2>nul
if not errorlevel 1 (
  echo [BLAD] Repo %REPO% juz istnieje. Uzyj opcji 4 i 7.
  pause
  goto MENU
)
call :ENSURE_INITIAL_COMMIT
if errorlevel 1 goto COMMAND_FAILED
gh repo create "%REPO%" --public --source "%PUBLIC_DIR%" --remote origin --push
if errorlevel 1 goto COMMAND_FAILED
echo.
echo [OK] Publiczne repo zostalo utworzone.
echo https://github.com/%REPO%
echo.
pause
goto MENU

:COMMIT_PUSH
cls
call :SYNC_PUBLIC_COPY
if errorlevel 1 goto COMMAND_FAILED
call :REQUIRE_GIT_REPO
if errorlevel 1 goto COMMAND_FAILED
call :ENSURE_GH_AUTH
if errorlevel 1 goto COMMAND_FAILED
call :ENSURE_REMOTE
if errorlevel 1 goto COMMAND_FAILED
call :CHECK_REMOTE_BASE
if errorlevel 1 goto COMMAND_FAILED
call :WRITE_UPDATE_MANIFEST "%PUBLIC_DIR%\update.json"
if errorlevel 1 goto COMMAND_FAILED
echo Zmiany do wyslania:
git -C "%PUBLIC_DIR%" status --short
echo.
set "MSG="
set /p "MSG=Opis commita [Update SmartTube AA]: "
if not defined MSG set "MSG=Update SmartTube AA"
set "CONFIRM_PUSH="
set /p "CONFIRM_PUSH=Wpisz PUSH aby wykonac commit i push: "
if /I not "!CONFIRM_PUSH!"=="PUSH" (
  echo Anulowano.
  pause
  goto MENU
)
git -C "%PUBLIC_DIR%" add -A
if errorlevel 1 goto COMMAND_FAILED
git -C "%PUBLIC_DIR%" commit -m "!MSG!"
if errorlevel 1 echo [INFO] Brak nowych zmian do commita. Probuje push obecnego HEAD.
git -C "%PUBLIC_DIR%" push -u origin "%BRANCH%"
if errorlevel 1 goto COMMAND_FAILED
echo.
echo [OK] GitHub jest zaktualizowany.
echo.
pause
goto MENU

:PULL
cls
call :REQUIRE_GIT_REPO
if errorlevel 1 goto COMMAND_FAILED
call :ENSURE_REMOTE
if errorlevel 1 goto COMMAND_FAILED
git -C "%PUBLIC_DIR%" pull --ff-only origin "%BRANCH%"
if errorlevel 1 goto COMMAND_FAILED
echo.
pause
goto MENU

:BUILD_APK
cls
call :RUN_LOCAL_BUILD
if errorlevel 1 goto COMMAND_FAILED
echo.
pause
goto MENU

:RUN_LOCAL_BUILD
call :CHECK_SOURCE
if errorlevel 1 exit /b 1
set "JAVA11_HOME="
if defined SMARTUBE_JAVA_HOME if exist "%SMARTUBE_JAVA_HOME%\bin\java.exe" set "JAVA11_HOME=%SMARTUBE_JAVA_HOME%"
if not defined JAVA11_HOME if exist "C:\Program Files\Eclipse Adoptium\jdk-11.0.31.11-hotspot\bin\java.exe" set "JAVA11_HOME=C:\Program Files\Eclipse Adoptium\jdk-11.0.31.11-hotspot"
if not defined JAVA11_HOME (
  echo [BLAD] Nie znaleziono Java 11. Ustaw SMARTUBE_JAVA_HOME.
  exit /b 1
)
set "JAVA_HOME=!JAVA11_HOME!"
set "PATH=!JAVA_HOME!\bin;!PATH!"
set "GRADLE_CACHE=%LOCALAPPDATA%\SmartTubeGithubHelper\gradle-project-cache"
if not exist "!GRADLE_CACHE!" mkdir "!GRADLE_CACHE!"
echo Buduje Stmobile Debug arm64/universal...
pushd "%SOURCE_DIR%"
call gradlew.bat :smarttubetv:assembleStmobileDebug --console=plain --no-daemon --max-workers=1 --no-parallel --project-cache-dir "!GRADLE_CACHE!"
set "BUILD_CODE=!errorlevel!"
popd
if not "!BUILD_CODE!"=="0" exit /b !BUILD_CODE!
call :FIND_LATEST_APK
if not defined LATEST_APK (
  echo [BLAD] Build zakonczony, ale nie znaleziono arm64 APK.
  exit /b 1
)
if not defined LATEST_UNIVERSAL_APK (
  echo [BLAD] Build zakonczony, ale nie znaleziono universal APK.
  exit /b 1
)
if not exist "%APK_STORE%" mkdir "%APK_STORE%"
copy /y "!LATEST_APK!" "%APK_STORE%\" >nul
copy /y "!LATEST_UNIVERSAL_APK!" "%APK_STORE%\" >nul
echo [OK] APK arm64    : !LATEST_APK!
echo [OK] APK universal: !LATEST_UNIVERSAL_APK!
exit /b 0

:LOCAL_PACKAGE
cls
call :CREATE_RELEASE_PACKAGE
if errorlevel 1 goto COMMAND_FAILED
echo.
echo [OK] Paczka lokalna:
echo !PACKAGE_DIR!
start "" "!PACKAGE_DIR!"
echo.
pause
goto MENU

:CREATE_RELEASE_PACKAGE
echo [INFO] Sprawdzam repozytorium Git...
call :REQUIRE_GIT_REPO
if errorlevel 1 exit /b 1
echo [INFO] Odczytuje wersje stmobile...
call :GET_VERSION
if errorlevel 1 exit /b 1
echo [INFO] Szukam gotowych plikow APK...
call :FIND_LATEST_APK
if not defined LATEST_APK (
  echo [BLAD] Brak arm64 APK. Najpierw uzyj opcji 9.
  exit /b 1
)
if not defined LATEST_UNIVERSAL_APK (
  echo [BLAD] Brak universal APK. Najpierw uzyj opcji 9.
  exit /b 1
)
for %%F in ("!LATEST_APK!") do set "ARM64_SOURCE_NAME=%%~nxF"
for %%F in ("!LATEST_UNIVERSAL_APK!") do set "UNIVERSAL_SOURCE_NAME=%%~nxF"
set "EXPECTED_ARM64_NAME=SmartTube_mobile_!APP_VERSION!_arm64-v8a.apk"
set "EXPECTED_UNIVERSAL_NAME=SmartTube_mobile_!APP_VERSION!_universal.apk"
if /I not "!ARM64_SOURCE_NAME!"=="!EXPECTED_ARM64_NAME!" (
  echo [BLAD] APK arm64 pochodzi z innej wersji: !ARM64_SOURCE_NAME!
  echo Oczekiwano: !EXPECTED_ARM64_NAME!
  exit /b 1
)
if /I not "!UNIVERSAL_SOURCE_NAME!"=="!EXPECTED_UNIVERSAL_NAME!" (
  echo [BLAD] APK universal pochodzi z innej wersji: !UNIVERSAL_SOURCE_NAME!
  echo Oczekiwano: !EXPECTED_UNIVERSAL_NAME!
  exit /b 1
)
for /f "usebackq delims=" %%T in (`powershell -NoProfile -Command "Get-Date -Format yyyyMMdd-HHmmss"`) do set "STAMP=%%T"
set "PACKAGE_DIR=%RELEASES_DIR%\SmartTube-AA-!APP_VERSION!-!STAMP!"
if not exist "!PACKAGE_DIR!" mkdir "!PACKAGE_DIR!"
copy /y "!LATEST_APK!" "!PACKAGE_DIR!\!EXPECTED_ARM64_NAME!" >nul
copy /y "!LATEST_UNIVERSAL_APK!" "!PACKAGE_DIR!\!EXPECTED_UNIVERSAL_NAME!" >nul
copy /y "!LATEST_UNIVERSAL_APK!" "!PACKAGE_DIR!\SmartTube-AA-latest.apk" >nul
git -C "%PUBLIC_DIR%" archive --format=zip --output="!PACKAGE_DIR!\SmartTube-AA-!APP_VERSION!-source.zip" HEAD
if errorlevel 1 exit /b 1
copy /y "%~f0" "!PACKAGE_DIR!\%HELPER_NAME%" >nul
call :WRITE_UPDATE_MANIFEST "!PACKAGE_DIR!\update.json"
if errorlevel 1 exit /b 1
(
  echo SmartTube AA !APP_VERSION!
  echo.
  echo APK arm64: !EXPECTED_ARM64_NAME!
  echo APK universal: !EXPECTED_UNIVERSAL_NAME!
  echo Latest alias: SmartTube-AA-latest.apk
  echo OTA manifest: update.json ^(versionCode !APP_VERSION_CODE!^)
  echo Source: SmartTube-AA-!APP_VERSION!-source.zip
  echo Build type: stmobile debug/test build
  echo Package: app.smarttube.mobile
  echo.
  echo This project is based on SmartTube. Keep the original LICENSE and attribution.
) > "!PACKAGE_DIR!\RELEASE-NOTES.txt"
powershell -NoProfile -Command "$files=Get-ChildItem -LiteralPath '!PACKAGE_DIR!' -File | Where-Object Name -ne 'SHA256SUMS.txt'; $files | ForEach-Object { (Get-FileHash -Algorithm SHA256 -LiteralPath $_.FullName).Hash.ToLower() + '  ' + $_.Name } | Set-Content -Encoding ASCII -LiteralPath '!PACKAGE_DIR!\SHA256SUMS.txt'"
if errorlevel 1 exit /b 1
exit /b 0

:PUBLISH_RELEASE
cls
call :REQUIRE_GIT_REPO
if errorlevel 1 goto COMMAND_FAILED
call :ENSURE_GH_AUTH
if errorlevel 1 goto COMMAND_FAILED
call :ENSURE_REMOTE
if errorlevel 1 goto COMMAND_FAILED
call :CHECK_REMOTE_BASE
if errorlevel 1 goto COMMAND_FAILED
call :REQUIRE_CLEAN_TREE
if errorlevel 1 (
  echo Najpierw uzyj opcji 7, aby zsynchronizowac i zapisac zmiany.
  pause
  goto MENU
)
call :CREATE_RELEASE_PACKAGE
if errorlevel 1 goto COMMAND_FAILED
set "TAG_NAME=v!APP_VERSION!"
echo.
echo Release: !TAG_NAME!
echo Repo   : %REPO%
echo Pliki  : !PACKAGE_DIR!
echo.
gh release view "!TAG_NAME!" -R "%REPO%" >nul 2>nul
if not errorlevel 1 (
  echo [BLAD] Release !TAG_NAME! juz istnieje. Podbij wersje przed kolejnym wydaniem.
  pause
  goto MENU
)
set "CONFIRM_RELEASE="
set /p "CONFIRM_RELEASE=Wpisz RELEASE aby wyslac tag i pliki publicznie: "
if /I not "!CONFIRM_RELEASE!"=="RELEASE" (
  echo Anulowano. Lokalna paczka pozostaje bez zmian.
  pause
  goto MENU
)
git -C "%PUBLIC_DIR%" push origin "%BRANCH%"
if errorlevel 1 goto COMMAND_FAILED
call :ENSURE_RELEASE_TAG
if errorlevel 1 goto COMMAND_FAILED
gh release create "!TAG_NAME!" ^
  "!PACKAGE_DIR!\!EXPECTED_ARM64_NAME!" ^
  "!PACKAGE_DIR!\!EXPECTED_UNIVERSAL_NAME!" ^
  "!PACKAGE_DIR!\SmartTube-AA-latest.apk" ^
  "!PACKAGE_DIR!\update.json" ^
  "!PACKAGE_DIR!\SmartTube-AA-!APP_VERSION!-source.zip" ^
  "!PACKAGE_DIR!\SHA256SUMS.txt" ^
  -R "%REPO%" --title "SmartTube AA !APP_VERSION!" ^
  --notes-file "!PACKAGE_DIR!\RELEASE-NOTES.txt" --latest
if errorlevel 1 goto COMMAND_FAILED
echo.
echo [OK] Publiczny release jest gotowy:
echo https://github.com/%REPO%/releases/tag/!TAG_NAME!
echo.
pause
goto MENU

:ENSURE_RELEASE_TAG
set "HEAD_SHA="
set "LOCAL_TAG_SHA="
set "REMOTE_TAG_SHA="
for /f "usebackq delims=" %%S in (`git -C "%PUBLIC_DIR%" rev-parse HEAD`) do set "HEAD_SHA=%%S"
for /f "usebackq delims=" %%S in (`git -C "%PUBLIC_DIR%" rev-list -n 1 "!TAG_NAME!" 2^>nul`) do set "LOCAL_TAG_SHA=%%S"
for /f "tokens=1" %%S in ('git -C "%PUBLIC_DIR%" ls-remote --tags origin "refs/tags/!TAG_NAME!" 2^>nul') do set "REMOTE_TAG_SHA=%%S"
if defined LOCAL_TAG_SHA if /I not "!LOCAL_TAG_SHA!"=="!HEAD_SHA!" (
  echo [BLAD] Lokalny tag !TAG_NAME! wskazuje na inny commit.
  exit /b 1
)
if defined REMOTE_TAG_SHA if /I not "!REMOTE_TAG_SHA!"=="!HEAD_SHA!" (
  echo [BLAD] Zdalny tag !TAG_NAME! wskazuje na inny commit.
  exit /b 1
)
if not defined LOCAL_TAG_SHA (
  git -C "%PUBLIC_DIR%" tag -a "!TAG_NAME!" -m "Release !TAG_NAME!"
  if errorlevel 1 exit /b 1
)
if not defined REMOTE_TAG_SHA (
  git -C "%PUBLIC_DIR%" push origin "refs/tags/!TAG_NAME!"
  if errorlevel 1 exit /b 1
)
exit /b 0

:FULL_BACKUP
cls
call :REQUIRE_GIT_REPO
if errorlevel 1 goto COMMAND_FAILED
for /f "usebackq delims=" %%T in (`powershell -NoProfile -Command "Get-Date -Format yyyyMMdd-HHmmss"`) do set "STAMP=%%T"
set "BACKUP_DIR=%RELEASES_DIR%\BACKUP-!STAMP!"
if not exist "!BACKUP_DIR!" mkdir "!BACKUP_DIR!"
git -C "%PUBLIC_DIR%" bundle create "!BACKUP_DIR!\SmartTube-AA-all-refs.bundle" --all
if errorlevel 1 goto COMMAND_FAILED
git -C "%PUBLIC_DIR%" diff --binary > "!BACKUP_DIR!\working-tree.patch"
git -C "%PUBLIC_DIR%" diff --cached --binary > "!BACKUP_DIR!\staged.patch"
git -C "%PUBLIC_DIR%" status --short > "!BACKUP_DIR!\status.txt"
where tar >nul 2>nul
if not errorlevel 1 (
  tar -a -cf "!BACKUP_DIR!\SmartTube-AA-current-snapshot.zip" --exclude=.git --exclude=releases -C "%PUBLIC_DIR%" .
)
call :FIND_LATEST_APK
if defined LATEST_APK copy /y "!LATEST_APK!" "!BACKUP_DIR!\" >nul
echo.
echo [OK] Kopia zapisana w:
echo !BACKUP_DIR!
start "" "!BACKUP_DIR!"
echo.
pause
goto MENU

:RELEASE_INFO
cls
call :ENSURE_GH_AUTH
if errorlevel 1 goto COMMAND_FAILED
echo Najnowszy release:
gh release view -R "%REPO%"
echo.
set "DO_DOWNLOAD="
set /p "DO_DOWNLOAD=Pobrac wszystkie pliki najnowszego release? [T/N]: "
if /I "!DO_DOWNLOAD!"=="T" (
  set "DOWNLOAD_DIR=%RELEASES_DIR%\download-latest"
  if not exist "!DOWNLOAD_DIR!" mkdir "!DOWNLOAD_DIR!"
  gh release download -R "%REPO%" -D "!DOWNLOAD_DIR!" --clobber
  if errorlevel 1 goto COMMAND_FAILED
  start "" "!DOWNLOAD_DIR!"
)
echo.
pause
goto MENU

:OPEN_TARGET
if exist "%PUBLIC_DIR%\.git" (
  start "" "https://github.com/%REPO%"
) else (
  start "" "%PUBLIC_DIR%"
)
goto MENU

:CHECK_SOURCE
if not exist "%SOURCE_DIR%\settings.gradle" (
  echo [BLAD] Nie znaleziono projektu: %SOURCE_DIR%
  exit /b 1
)
if not exist "%SOURCE_DIR%\smarttubetv\build.gradle" (
  echo [BLAD] Brak smarttubetv\build.gradle.
  exit /b 1
)
exit /b 0

:CHECK_GIT_TOOL
where git >nul 2>nul
if errorlevel 1 (
  echo [BLAD] Nie znaleziono Git for Windows.
  exit /b 1
)
exit /b 0

:CHECK_GH_TOOL
where gh >nul 2>nul
if errorlevel 1 (
  echo [BLAD] Nie znaleziono GitHub CLI: gh.
  exit /b 1
)
exit /b 0

:ENSURE_GH_AUTH
call :CHECK_GH_TOOL
if errorlevel 1 exit /b 1
gh auth status --hostname github.com >nul 2>nul
if errorlevel 1 (
  echo [BLAD] GitHub CLI nie jest zalogowany. Uzyj opcji 5.
  exit /b 1
)
gh auth setup-git >nul 2>nul
exit /b 0

:REQUIRE_GIT_REPO
call :CHECK_GIT_TOOL
if errorlevel 1 exit /b 1
if not exist "%PUBLIC_DIR%\.git" (
  echo [BLAD] Brak repo Git w %PUBLIC_DIR%. Uzyj opcji 2 i 3.
  exit /b 1
)
exit /b 0

:ENSURE_REMOTE
set "ACTUAL_REMOTE="
for /f "usebackq delims=" %%U in (`git -C "%PUBLIC_DIR%" remote get-url origin 2^>nul`) do set "ACTUAL_REMOTE=%%U"
if not defined ACTUAL_REMOTE (
  git -C "%PUBLIC_DIR%" remote add origin "https://github.com/%REPO%.git"
  if errorlevel 1 exit /b 1
  set "ACTUAL_REMOTE=https://github.com/%REPO%.git"
)
set "REMOTE_MATCH="
if /I "!ACTUAL_REMOTE!"=="https://github.com/%REPO%.git" set "REMOTE_MATCH=1"
if /I "!ACTUAL_REMOTE!"=="https://github.com/%REPO%" set "REMOTE_MATCH=1"
if /I "!ACTUAL_REMOTE!"=="git@github.com:%REPO%.git" set "REMOTE_MATCH=1"
if not defined REMOTE_MATCH (
  echo [BLAD] Origin wskazuje inne repozytorium:
  echo !ACTUAL_REMOTE!
  echo Oczekiwano: https://github.com/%REPO%.git
  echo Uzyj opcji 4 tylko wtedy, gdy swiadomie chcesz zmienic repo.
  exit /b 1
)
set "CURRENT_BRANCH="
for /f "usebackq delims=" %%B in (`git -C "%PUBLIC_DIR%" branch --show-current`) do set "CURRENT_BRANCH=%%B"
if /I not "!CURRENT_BRANCH!"=="%BRANCH%" (
  echo [BLAD] Aktywna galaz to !CURRENT_BRANCH!, a helper oczekuje %BRANCH%.
  exit /b 1
)
exit /b 0

:CHECK_REMOTE_BASE
git -C "%PUBLIC_DIR%" fetch origin "%BRANCH%" --quiet
if errorlevel 1 exit /b 1
git -C "%PUBLIC_DIR%" merge-base --is-ancestor "origin/%BRANCH%" HEAD
if errorlevel 1 (
  echo [BLAD] Zdalna galaz origin/%BRANCH% zawiera zmiany, ktorych nie ma lokalnie.
  echo Uzyj opcji 8 ^(pull --ff-only^) i sprawdz projekt przed wyslaniem.
  exit /b 1
)
exit /b 0

:ENSURE_INITIAL_COMMIT
git -C "%PUBLIC_DIR%" rev-parse --verify HEAD >nul 2>nul
if not errorlevel 1 exit /b 0
git -C "%PUBLIC_DIR%" add -A
if errorlevel 1 exit /b 1
git -C "%PUBLIC_DIR%" commit -m "Initial public SmartTube AA source"
exit /b %errorlevel%

:REQUIRE_CLEAN_TREE
set "DIRTY_TREE="
for /f "usebackq delims=" %%L in (`git -C "%PUBLIC_DIR%" status --porcelain`) do set "DIRTY_TREE=1"
if defined DIRTY_TREE (
  echo [BLAD] Repo ma niezapisane zmiany:
  git -C "%PUBLIC_DIR%" status --short
  exit /b 1
)
exit /b 0

:GET_VERSION
set "BASE_VERSION="
set "VERSION_SUFFIX="
set "APP_VERSION="
set "APP_VERSION_CODE="
set "VERSION_GRADLE=%SOURCE_DIR%\smarttubetv\build.gradle"
for /f "tokens=2" %%V in ('findstr /C:"versionName " "%VERSION_GRADLE%"') do if not defined BASE_VERSION set "BASE_VERSION=%%~V"
for /f "tokens=2" %%V in ('findstr /C:"versionNameSuffix " "%VERSION_GRADLE%"') do (
  set "SUFFIX_CANDIDATE=%%~V"
  if /I "!SUFFIX_CANDIDATE:~0,7!"=="-mobile" set "VERSION_SUFFIX=!SUFFIX_CANDIDATE!"
)
for /f "tokens=2" %%V in ('findstr /C:"versionCode " "%VERSION_GRADLE%"') do if not defined APP_VERSION_CODE set "APP_VERSION_CODE=%%~V"
if not defined BASE_VERSION (
  echo [BLAD] Nie udalo sie odczytac versionName.
  exit /b 1
)
if not defined VERSION_SUFFIX (
  echo [BLAD] Nie udalo sie odczytac wersji wariantu stmobile.
  exit /b 1
)
if not defined APP_VERSION_CODE (
  echo [BLAD] Nie udalo sie odczytac versionCode.
  exit /b 1
)
set "APP_VERSION=!BASE_VERSION!!VERSION_SUFFIX!"
exit /b 0

:WRITE_UPDATE_MANIFEST
call :GET_VERSION
if errorlevel 1 exit /b 1
set "MANIFEST_PATH=%~1"
powershell -NoProfile -Command "$p=$env:MANIFEST_PATH; $u='https://github.com/'+$env:REPO+'/releases/latest/download/SmartTube-AA-latest.apk'; $ok=$false; if(Test-Path -LiteralPath $p){try{$o=Get-Content -Raw -LiteralPath $p|ConvertFrom-Json; $e=$o.PSObject.Properties[$env:APP_VERSION].Value; $ok=$o.package.downloadUrl -eq $u -and [int]$e.versionCode -eq [int]$env:APP_VERSION_CODE}catch{}}; if($ok){exit 0}; $m=[ordered]@{package=[ordered]@{downloadUrl=$u}; $env:APP_VERSION=[ordered]@{versionCode=[int]$env:APP_VERSION_CODE}}; $j=$m|ConvertTo-Json -Depth 4; [IO.File]::WriteAllText($p,$j+[Environment]::NewLine,(New-Object Text.UTF8Encoding($false)))"
if errorlevel 1 (
  echo [BLAD] Nie udalo sie utworzyc update.json.
  exit /b 1
)
exit /b 0

:FIND_LATEST_APK
set "LATEST_APK="
set "LATEST_UNIVERSAL_APK="
for /f "delims=" %%F in ('dir /b /a-d /o-d "%BUILD_APK_DIR%\*arm64-v8a.apk" 2^>nul') do if not defined LATEST_APK set "LATEST_APK=%BUILD_APK_DIR%\%%F"
if not defined LATEST_APK for /f "delims=" %%F in ('dir /b /a-d /o-d "%APK_STORE%\*arm64-v8a.apk" 2^>nul') do if not defined LATEST_APK set "LATEST_APK=%APK_STORE%\%%F"
for /f "delims=" %%F in ('dir /b /a-d /o-d "%BUILD_APK_DIR%\*universal.apk" 2^>nul') do if not defined LATEST_UNIVERSAL_APK set "LATEST_UNIVERSAL_APK=%BUILD_APK_DIR%\%%F"
if not defined LATEST_UNIVERSAL_APK for /f "delims=" %%F in ('dir /b /a-d /o-d "%APK_STORE%\*universal.apk" 2^>nul') do if not defined LATEST_UNIVERSAL_APK set "LATEST_UNIVERSAL_APK=%APK_STORE%\%%F"
exit /b 0

:COMMAND_FAILED
echo.
echo [BLAD] Operacja nie zostala zakonczona.
echo Sprawdz komunikaty powyzej. Helper nie wykonuje automatycznego resetu ani kasowania repo.
echo.
pause
goto MENU
