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
set "MUSIC_APK_STORE=%APK_STORE%\music"
set "VIDEO_APK_STORE=%APK_STORE%\video"
set "BUILD_APK_DIR=%SOURCE_DIR%\smarttubetv\build\outputs\apk\stmobile\debug"
set "BUILD_VIDEO_APK_DIR=%SOURCE_DIR%\smarttubetv\build\outputs\apk\stmobile\carvideo"
set "SMARTUBE_MOBILE_PACKAGE=app.smarttube.mobile"
set "SMARTUBE_VIDEO_PACKAGE=app.smarttube.mobile.carvideo"
set "ADB_WAIT_SECONDS=60"
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
echo  1 - Status + pelna kontrola zgodnosci z GitHub
echo  2 - Sprawdz przenosny katalog projektu
echo  3 - Zainicjuj Git w tym projekcie
echo  4 - Ustaw repo GitHub i remote origin
echo  5 - Zaloguj/sprawdz GitHub CLI
echo  6 - Utworz PUBLICZNE repo GitHub i wykonaj pierwszy push
echo  7 - ADB telefon - wywolaj autoryzacje USB/RSA i poczekaj
echo  8 - TELEFON: ostatni APK arm64 - instaluj i uruchom ^(S26 Ultra^)
echo  9 - TELEFON: build APK + instalacja i start ^(S26 Ultra / arm64^)
echo 10 - EMULATOR: build APK + instalacja i start ^(NIE instaluje na telefonie^)
echo 11 - Diagnostyka ADB i wersji aplikacji
echo 12 - Synchronizuj + commit + push
echo 13 - Pull --ff-only z GitHub
echo 14 - Zbuduj Music + Video EXP lokalnie
echo 15 - Utworz lokalna paczke release obu aplikacji
echo 16 - Opublikuj GitHub Release: Music + Video EXP + zrodla
echo 17 - Pelna lokalna kopia Git: bundle + snapshot + patche
echo 18 - Pokaz/pobierz najnowszy GitHub Release
echo 19 - Otworz repo lub katalog publiczny
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
if "%OPT%"=="7" goto ADB_AUTHORIZE
if "%OPT%"=="8" goto PHONE_INSTALL
if "%OPT%"=="9" goto PHONE_BUILD_INSTALL
if "%OPT%"=="10" goto EMULATOR_BUILD_INSTALL
if "%OPT%"=="11" goto ADB_DIAGNOSTICS
if "%OPT%"=="12" goto COMMIT_PUSH
if "%OPT%"=="13" goto PULL
if "%OPT%"=="14" goto BUILD_APK
if "%OPT%"=="15" goto LOCAL_PACKAGE
if "%OPT%"=="16" goto PUBLISH_RELEASE
if "%OPT%"=="17" goto FULL_BACKUP
if "%OPT%"=="18" goto RELEASE_INFO
if "%OPT%"=="19" goto OPEN_TARGET
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
  echo [OK] Katalog wynikow Music istnieje.
) else (
  echo [INFO] Katalog Music APK powstanie po buildzie.
)
if exist "%BUILD_VIDEO_APK_DIR%" (
  echo [OK] Katalog wynikow Video EXP istnieje.
) else (
  echo [INFO] Katalog Video EXP APK powstanie po buildzie.
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
call :GET_VERSION
if errorlevel 1 exit /b 1
echo ============================================================
echo Projekt          : %PROJECT_DIR%
echo Repo GitHub      : %REPO%
echo Branch           : %BRANCH%
echo Wersja zrodel    : !APP_VERSION! ^(versionCode !APP_VERSION_CODE!^)
echo ============================================================
if exist "%PUBLIC_DIR%\.git" (
  echo.
  git -C "%PUBLIC_DIR%" status --short --branch
  echo.
  git -C "%PUBLIC_DIR%" remote -v
  echo.
  call :CHECK_GITHUB_SYNC
) else (
  echo [INFO] Projekt nie ma jeszcze Git. Uzyj opcji 3.
)
echo.
call :FIND_LATEST_APK
if defined LATEST_APK (
  echo APK arm64    : !LATEST_APK!
) else (
  echo [INFO] Brak gotowego APK arm64. Uzyj opcji 14.
)
if defined LATEST_UNIVERSAL_APK (
  echo APK universal: !LATEST_UNIVERSAL_APK!
) else (
  echo [INFO] Brak gotowego APK universal. Uzyj opcji 14.
)
if defined LATEST_VIDEO_APK (
  echo Video arm64   : !LATEST_VIDEO_APK!
) else (
  echo [INFO] Brak Video EXP arm64. Uzyj opcji 14.
)
if defined LATEST_VIDEO_UNIVERSAL_APK (
  echo Video universal: !LATEST_VIDEO_UNIVERSAL_APK!
) else (
  echo [INFO] Brak Video EXP universal. Uzyj opcji 14.
)
echo.
call :CHECK_FOUND_APK_VERSIONS
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
  echo [BLAD] Repo %REPO% juz istnieje. Uzyj opcji 4 i 12.
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
call :GET_VERSION
if errorlevel 1 goto COMMAND_FAILED
echo ============================================================
echo WERSJA, KTORA BEDZIE WYSYLANA
echo ============================================================
echo Projekt     : %PROJECT_DIR%
echo Repo        : %REPO%
echo Branch      : %BRANCH%
echo App version : !APP_VERSION!
echo VersionCode : !APP_VERSION_CODE!
echo ============================================================
echo.
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
call :REQUIRE_CLEAN_TREE
if errorlevel 1 goto COMMAND_FAILED
git -C "%PUBLIC_DIR%" push -u origin "%BRANCH%"
if errorlevel 1 goto COMMAND_FAILED
call :VERIFY_GITHUB_EXACT
if errorlevel 1 goto COMMAND_FAILED
echo.
echo [OK] GitHub jest zaktualizowany i zweryfikowany 1:1 z lokalnym HEAD.
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

:ADB_AUTHORIZE
cls
call :PREPARE_ADB
if errorlevel 1 goto COMMAND_FAILED
call :AUTHORIZE_AND_SELECT_PHONE
if errorlevel 1 goto COMMAND_FAILED
echo.
echo [OK] Telefon jest autoryzowany dla ADB: !SELECTED_MODEL! [!SELECTED_DEVICE!]
echo [INFO] ABI telefonu: !SELECTED_ABI!
echo.
pause
goto MENU

:PHONE_INSTALL
cls
call :PREPARE_ADB
if errorlevel 1 goto COMMAND_FAILED
call :AUTHORIZE_AND_SELECT_PHONE
if errorlevel 1 goto COMMAND_FAILED
call :SELECT_SMARTUBE_APK_FOR_DEVICE "!SELECTED_DEVICE!"
if errorlevel 1 goto COMMAND_FAILED
set "TARGET_DEVICE=!SELECTED_DEVICE!"
set "TARGET_PACKAGE=%SMARTUBE_MOBILE_PACKAGE%"
call :INSTALL_AND_LAUNCH
if errorlevel 1 goto COMMAND_FAILED
echo.
echo [OK] SmartTube Mobile zainstalowany i uruchomiony na telefonie.
echo [OK] Wybrany APK: !TARGET_APK!
echo.
pause
goto MENU

:PHONE_BUILD_INSTALL
cls
call :RUN_MOBILE_BUILD_ONLY
if errorlevel 1 goto COMMAND_FAILED
call :PREPARE_ADB
if errorlevel 1 goto COMMAND_FAILED
call :AUTHORIZE_AND_SELECT_PHONE
if errorlevel 1 goto COMMAND_FAILED
call :SELECT_SMARTUBE_APK_FOR_DEVICE "!SELECTED_DEVICE!"
if errorlevel 1 goto COMMAND_FAILED
set "TARGET_DEVICE=!SELECTED_DEVICE!"
set "TARGET_PACKAGE=%SMARTUBE_MOBILE_PACKAGE%"
call :INSTALL_AND_LAUNCH
if errorlevel 1 goto COMMAND_FAILED
echo.
echo [OK] Build + instalacja + start na telefonie zakonczone.
echo [OK] Wybrany APK: !TARGET_APK!
echo.
pause
goto MENU

:EMULATOR_BUILD_INSTALL
cls
call :RUN_MOBILE_BUILD_ONLY
if errorlevel 1 goto COMMAND_FAILED
call :PREPARE_ADB
if errorlevel 1 goto COMMAND_FAILED
call :SELECT_EMULATOR
if errorlevel 1 goto COMMAND_FAILED
call :SELECT_SMARTUBE_APK_FOR_DEVICE "!SELECTED_DEVICE!"
if errorlevel 1 goto COMMAND_FAILED
set "TARGET_DEVICE=!SELECTED_DEVICE!"
set "TARGET_PACKAGE=%SMARTUBE_MOBILE_PACKAGE%"
call :INSTALL_AND_LAUNCH
if errorlevel 1 goto COMMAND_FAILED
echo.
echo [OK] Build + instalacja + start na emulatorze zakonczone.
echo [INFO] Telefon nie zostal wybrany ani uzyty.
echo [OK] Wybrany APK: !TARGET_APK!
echo.
pause
goto MENU

:ADB_DIAGNOSTICS
cls
call :PREPARE_ADB
if errorlevel 1 goto COMMAND_FAILED
call :SHOW_ADB_DIAGNOSTICS
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
echo Buduje Music arm64/universal...
pushd "%SOURCE_DIR%"
call gradlew.bat :smarttubetv:assembleStmobileDebug --console=plain --no-daemon --max-workers=1 --no-parallel --project-cache-dir "!GRADLE_CACHE!"
set "BUILD_CODE=!errorlevel!"
if "!BUILD_CODE!"=="0" (
  echo.
  echo Buduje Video EXP arm64/universal...
  call gradlew.bat :smarttubetv:assembleStmobileCarvideo --console=plain --no-daemon --max-workers=1 --no-parallel --project-cache-dir "!GRADLE_CACHE!"
  set "BUILD_CODE=!errorlevel!"
)
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
if not defined LATEST_VIDEO_APK (
  echo [BLAD] Build zakonczony, ale nie znaleziono Video EXP arm64 APK.
  exit /b 1
)
if not defined LATEST_VIDEO_UNIVERSAL_APK (
  echo [BLAD] Build zakonczony, ale nie znaleziono Video EXP universal APK.
  exit /b 1
)
if not exist "%MUSIC_APK_STORE%" mkdir "%MUSIC_APK_STORE%"
if not exist "%VIDEO_APK_STORE%" mkdir "%VIDEO_APK_STORE%"
copy /y "!LATEST_APK!" "%MUSIC_APK_STORE%\" >nul
copy /y "!LATEST_UNIVERSAL_APK!" "%MUSIC_APK_STORE%\" >nul
copy /y "!LATEST_VIDEO_APK!" "%VIDEO_APK_STORE%\" >nul
copy /y "!LATEST_VIDEO_UNIVERSAL_APK!" "%VIDEO_APK_STORE%\" >nul
echo [OK] Music arm64     : !LATEST_APK!
echo [OK] Music universal : !LATEST_UNIVERSAL_APK!
echo [OK] Video arm64     : !LATEST_VIDEO_APK!
echo [OK] Video universal : !LATEST_VIDEO_UNIVERSAL_APK!
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
  echo [BLAD] Brak arm64 APK. Najpierw uzyj opcji 14.
  exit /b 1
)
if not defined LATEST_UNIVERSAL_APK (
  echo [BLAD] Brak universal APK. Najpierw uzyj opcji 14.
  exit /b 1
)
if not defined LATEST_VIDEO_APK (
  echo [BLAD] Brak Video EXP arm64 APK. Najpierw uzyj opcji 14.
  exit /b 1
)
if not defined LATEST_VIDEO_UNIVERSAL_APK (
  echo [BLAD] Brak Video EXP universal APK. Najpierw uzyj opcji 14.
  exit /b 1
)
for %%F in ("!LATEST_APK!") do set "ARM64_SOURCE_NAME=%%~nxF"
for %%F in ("!LATEST_UNIVERSAL_APK!") do set "UNIVERSAL_SOURCE_NAME=%%~nxF"
for %%F in ("!LATEST_VIDEO_APK!") do set "VIDEO_ARM64_SOURCE_NAME=%%~nxF"
for %%F in ("!LATEST_VIDEO_UNIVERSAL_APK!") do set "VIDEO_UNIVERSAL_SOURCE_NAME=%%~nxF"
set "EXPECTED_ARM64_NAME=SmartTube_mobile_!APP_VERSION!_arm64-v8a.apk"
set "EXPECTED_UNIVERSAL_NAME=SmartTube_mobile_!APP_VERSION!_universal.apk"
set "EXPECTED_VIDEO_ARM64_NAME=SmartTube_mobile_!APP_VERSION!-carvideo_arm64-v8a.apk"
set "EXPECTED_VIDEO_UNIVERSAL_NAME=SmartTube_mobile_!APP_VERSION!-carvideo_universal.apk"
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
if /I not "!VIDEO_ARM64_SOURCE_NAME!"=="!EXPECTED_VIDEO_ARM64_NAME!" (
  echo [BLAD] Video arm64 pochodzi z innej wersji: !VIDEO_ARM64_SOURCE_NAME!
  echo Oczekiwano: !EXPECTED_VIDEO_ARM64_NAME!
  exit /b 1
)
if /I not "!VIDEO_UNIVERSAL_SOURCE_NAME!"=="!EXPECTED_VIDEO_UNIVERSAL_NAME!" (
  echo [BLAD] Video universal pochodzi z innej wersji: !VIDEO_UNIVERSAL_SOURCE_NAME!
  echo Oczekiwano: !EXPECTED_VIDEO_UNIVERSAL_NAME!
  exit /b 1
)
set "MUSIC_ARM64_ASSET=SmartTube-AA-Music_!APP_VERSION!_arm64-v8a.apk"
set "MUSIC_UNIVERSAL_ASSET=SmartTube-AA-Music_!APP_VERSION!_universal.apk"
set "VIDEO_ARM64_ASSET=SmartTube-AA-Video-EXP_!APP_VERSION!_arm64-v8a.apk"
set "VIDEO_UNIVERSAL_ASSET=SmartTube-AA-Video-EXP_!APP_VERSION!_universal.apk"
for /f "usebackq delims=" %%T in (`powershell -NoProfile -Command "Get-Date -Format yyyyMMdd-HHmmss"`) do set "STAMP=%%T"
set "PACKAGE_DIR=%RELEASES_DIR%\SmartTube-AA-!APP_VERSION!-!STAMP!"
if not exist "!PACKAGE_DIR!" mkdir "!PACKAGE_DIR!"
copy /y "!LATEST_APK!" "!PACKAGE_DIR!\!MUSIC_ARM64_ASSET!" >nul
copy /y "!LATEST_UNIVERSAL_APK!" "!PACKAGE_DIR!\!MUSIC_UNIVERSAL_ASSET!" >nul
copy /y "!LATEST_VIDEO_APK!" "!PACKAGE_DIR!\!VIDEO_ARM64_ASSET!" >nul
copy /y "!LATEST_VIDEO_UNIVERSAL_APK!" "!PACKAGE_DIR!\!VIDEO_UNIVERSAL_ASSET!" >nul
copy /y "!LATEST_UNIVERSAL_APK!" "!PACKAGE_DIR!\SmartTube-AA-latest.apk" >nul
copy /y "!LATEST_VIDEO_UNIVERSAL_APK!" "!PACKAGE_DIR!\SmartTube-AA-Video-EXP-latest.apk" >nul
git -C "%PUBLIC_DIR%" archive --format=zip --output="!PACKAGE_DIR!\SmartTube-AA-!APP_VERSION!-source.zip" HEAD
if errorlevel 1 exit /b 1
copy /y "%~f0" "!PACKAGE_DIR!\%HELPER_NAME%" >nul
call :WRITE_UPDATE_MANIFEST "!PACKAGE_DIR!\update.json"
if errorlevel 1 exit /b 1
(
  echo SmartTube AA !APP_VERSION!
  echo.
  echo MUSIC - stable Android Auto audio application
  echo Music arm64: !MUSIC_ARM64_ASSET!
  echo Music universal: !MUSIC_UNIVERSAL_ASSET!
  echo Music latest alias: SmartTube-AA-latest.apk
  echo Package: app.smarttube.mobile
  echo.
  echo VIDEO EXP - separate experimental parked-video installation
  echo Video arm64: !VIDEO_ARM64_ASSET!
  echo Video universal: !VIDEO_UNIVERSAL_ASSET!
  echo Video latest alias: SmartTube-AA-Video-EXP-latest.apk
  echo Package: app.smarttube.mobile.carvideo
  echo This is not the driving-safe Music interface. Use only while parked and where allowed.
  echo.
  echo OTA manifest: update.json ^(versionCode !APP_VERSION_CODE!^)
  echo Source: SmartTube-AA-!APP_VERSION!-source.zip
  echo Build types: stmobile debug + stmobile carvideo
  echo Video EXP updates are manual in this release; update.json remains Music-only.
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
  echo Najpierw uzyj opcji 12, aby zsynchronizowac i zapisac zmiany.
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
call :VERIFY_GITHUB_EXACT
if errorlevel 1 goto COMMAND_FAILED
call :ENSURE_RELEASE_TAG
if errorlevel 1 goto COMMAND_FAILED
gh release create "!TAG_NAME!" ^
  "!PACKAGE_DIR!\!MUSIC_ARM64_ASSET!" ^
  "!PACKAGE_DIR!\!MUSIC_UNIVERSAL_ASSET!" ^
  "!PACKAGE_DIR!\!VIDEO_ARM64_ASSET!" ^
  "!PACKAGE_DIR!\!VIDEO_UNIVERSAL_ASSET!" ^
  "!PACKAGE_DIR!\SmartTube-AA-latest.apk" ^
  "!PACKAGE_DIR!\SmartTube-AA-Video-EXP-latest.apk" ^
  "!PACKAGE_DIR!\update.json" ^
  "!PACKAGE_DIR!\SmartTube-AA-!APP_VERSION!-source.zip" ^
  "!PACKAGE_DIR!\%HELPER_NAME%" ^
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
if defined LATEST_UNIVERSAL_APK copy /y "!LATEST_UNIVERSAL_APK!" "!BACKUP_DIR!\" >nul
if defined LATEST_VIDEO_APK copy /y "!LATEST_VIDEO_APK!" "!BACKUP_DIR!\" >nul
if defined LATEST_VIDEO_UNIVERSAL_APK copy /y "!LATEST_VIDEO_UNIVERSAL_APK!" "!BACKUP_DIR!\" >nul
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

:CHECK_GITHUB_SYNC
call :CHECK_GIT_TOOL
if errorlevel 1 exit /b 1

set "SYNC_LOCAL_SHA="
set "SYNC_REMOTE_SHA="
set "SYNC_CURRENT_BRANCH="
set "SYNC_AHEAD=0"
set "SYNC_BEHIND=0"
set "SYNC_DIRTY="

echo ============================================================
echo KONTROLA ZGODNOSCI Z GITHUB
echo ============================================================
echo [INFO] Odswiezam origin/%BRANCH% przez git fetch...
git -C "%PUBLIC_DIR%" fetch origin "%BRANCH%" --quiet
if errorlevel 1 (
  echo [BLAD] git fetch nie powiodl sie.
  echo        Nie moge potwierdzic, czy lokalny projekt jest aktualny.
  exit /b 1
)

for /f "usebackq delims=" %%S in (`git -C "%PUBLIC_DIR%" rev-parse HEAD 2^>nul`) do set "SYNC_LOCAL_SHA=%%S"
for /f "usebackq delims=" %%S in (`git -C "%PUBLIC_DIR%" rev-parse "origin/%BRANCH%" 2^>nul`) do set "SYNC_REMOTE_SHA=%%S"
for /f "usebackq delims=" %%B in (`git -C "%PUBLIC_DIR%" branch --show-current 2^>nul`) do set "SYNC_CURRENT_BRANCH=%%B"
for /f "tokens=1,2" %%A in ('git -C "%PUBLIC_DIR%" rev-list --left-right --count HEAD...origin/%BRANCH% 2^>nul') do (
  set "SYNC_AHEAD=%%A"
  set "SYNC_BEHIND=%%B"
)
for /f "usebackq delims=" %%L in (`git -C "%PUBLIC_DIR%" status --porcelain 2^>nul`) do set "SYNC_DIRTY=1"

echo Aktywna galaz : !SYNC_CURRENT_BRANCH!
echo Lokalny HEAD  : !SYNC_LOCAL_SHA!
echo GitHub        : !SYNC_REMOTE_SHA!
echo Ahead/behind  : +!SYNC_AHEAD! / -!SYNC_BEHIND!
echo.

if /I not "!SYNC_CURRENT_BRANCH!"=="%BRANCH%" (
  echo [BLAD] Jestes na galezi !SYNC_CURRENT_BRANCH!, helper oczekuje %BRANCH%.
  echo        Przed buildem/pushem sprawdz aktywna galaz.
) else if /I "!SYNC_LOCAL_SHA!"=="!SYNC_REMOTE_SHA!" (
  echo [OK] LOKALNY PROJEKT JEST DOKLADNIE ZGODNY Z origin/%BRANCH%.
) else (
  git -C "%PUBLIC_DIR%" merge-base --is-ancestor HEAD "origin/%BRANCH%" >nul 2>nul
  if not errorlevel 1 (
    echo [UWAGA] GitHub ma nowsze commity. Lokalny projekt jest !SYNC_BEHIND! commit^(ow^) do tylu.
    echo         Uzyj opcji 13: Pull --ff-only z GitHub.
  ) else (
    git -C "%PUBLIC_DIR%" merge-base --is-ancestor "origin/%BRANCH%" HEAD >nul 2>nul
    if not errorlevel 1 (
      echo [INFO] Lokalny projekt ma !SYNC_AHEAD! commit^(ow^) wiecej niz GitHub.
      echo        Jesli to zamierzone, uzyj opcji 12 aby wykonac push.
    ) else (
      echo [BLAD] Lokalna i zdalna historia sie rozeszly.
      echo        Nie wykonuj automatycznego push/pull bez sprawdzenia zmian.
    )
  )
)

if defined SYNC_DIRTY (
  echo [UWAGA] Katalog roboczy ma NIEZAPISANE zmiany:
  git -C "%PUBLIC_DIR%" status --short
) else (
  echo [OK] Katalog roboczy jest czysty - brak niezacommitowanych zmian.
)

echo ============================================================
exit /b 0

:CHECK_FOUND_APK_VERSIONS
set "APK_VERSION_WARN="
set "EXPECTED_ARM64_NAME=SmartTube_mobile_!APP_VERSION!_arm64-v8a.apk"
set "EXPECTED_UNIVERSAL_NAME=SmartTube_mobile_!APP_VERSION!_universal.apk"
set "EXPECTED_VIDEO_ARM64_NAME=SmartTube_mobile_!APP_VERSION!-carvideo_arm64-v8a.apk"
set "EXPECTED_VIDEO_UNIVERSAL_NAME=SmartTube_mobile_!APP_VERSION!-carvideo_universal.apk"

echo ============================================================
echo KONTROLA WERSJI GOTOWYCH APK
echo ============================================================

if defined LATEST_APK (
  for %%F in ("!LATEST_APK!") do set "FOUND_NAME=%%~nxF"
  if /I "!FOUND_NAME!"=="!EXPECTED_ARM64_NAME!" (
    echo [OK] Music arm64 odpowiada aktualnym zrodlom: !FOUND_NAME!
  ) else (
    echo [UWAGA] Music arm64 jest z innej wersji: !FOUND_NAME!
    echo         Oczekiwano: !EXPECTED_ARM64_NAME!
    set "APK_VERSION_WARN=1"
  )
)

if defined LATEST_UNIVERSAL_APK (
  for %%F in ("!LATEST_UNIVERSAL_APK!") do set "FOUND_NAME=%%~nxF"
  if /I "!FOUND_NAME!"=="!EXPECTED_UNIVERSAL_NAME!" (
    echo [OK] Music universal odpowiada aktualnym zrodlom: !FOUND_NAME!
  ) else (
    echo [UWAGA] Music universal jest z innej wersji: !FOUND_NAME!
    echo         Oczekiwano: !EXPECTED_UNIVERSAL_NAME!
    set "APK_VERSION_WARN=1"
  )
)

if defined LATEST_VIDEO_APK (
  for %%F in ("!LATEST_VIDEO_APK!") do set "FOUND_NAME=%%~nxF"
  if /I "!FOUND_NAME!"=="!EXPECTED_VIDEO_ARM64_NAME!" (
    echo [OK] Video arm64 odpowiada aktualnym zrodlom: !FOUND_NAME!
  ) else (
    echo [UWAGA] Video arm64 jest z innej wersji: !FOUND_NAME!
    echo         Oczekiwano: !EXPECTED_VIDEO_ARM64_NAME!
    set "APK_VERSION_WARN=1"
  )
)

if defined LATEST_VIDEO_UNIVERSAL_APK (
  for %%F in ("!LATEST_VIDEO_UNIVERSAL_APK!") do set "FOUND_NAME=%%~nxF"
  if /I "!FOUND_NAME!"=="!EXPECTED_VIDEO_UNIVERSAL_NAME!" (
    echo [OK] Video universal odpowiada aktualnym zrodlom: !FOUND_NAME!
  ) else (
    echo [UWAGA] Video universal jest z innej wersji: !FOUND_NAME!
    echo         Oczekiwano: !EXPECTED_VIDEO_UNIVERSAL_NAME!
    set "APK_VERSION_WARN=1"
  )
)

if defined APK_VERSION_WARN (
  echo [UWAGA] Przed GitHub Release wykonaj opcje 14, aby przebudowac APK.
) else (
  echo [OK] Znalezione APK maja wersje zgodna z aktualnymi zrodlami.
)
echo ============================================================
exit /b 0

:VERIFY_GITHUB_EXACT
set "VERIFY_LOCAL_SHA="
set "VERIFY_REMOTE_SHA="
echo.
echo ============================================================
echo KONCOWA WERYFIKACJA GITHUB
echo ============================================================
echo [INFO] Pobieram aktualny origin/%BRANCH%...
git -C "%PUBLIC_DIR%" fetch origin "%BRANCH%" --quiet
if errorlevel 1 (
  echo [BLAD] Nie udalo sie odswiezyc GitHub po push.
  exit /b 1
)
for /f "usebackq delims=" %%S in (`git -C "%PUBLIC_DIR%" rev-parse HEAD 2^>nul`) do set "VERIFY_LOCAL_SHA=%%S"
for /f "usebackq delims=" %%S in (`git -C "%PUBLIC_DIR%" rev-parse "origin/%BRANCH%" 2^>nul`) do set "VERIFY_REMOTE_SHA=%%S"
echo Lokalny HEAD : !VERIFY_LOCAL_SHA!
echo GitHub HEAD  : !VERIFY_REMOTE_SHA!
if not defined VERIFY_LOCAL_SHA (
  echo [BLAD] Nie udalo sie odczytac lokalnego HEAD.
  exit /b 1
)
if not defined VERIFY_REMOTE_SHA (
  echo [BLAD] Nie udalo sie odczytac origin/%BRANCH%.
  exit /b 1
)
if /I not "!VERIFY_LOCAL_SHA!"=="!VERIFY_REMOTE_SHA!" (
  echo [BLAD] GitHub NIE wskazuje na ten sam commit co lokalny projekt.
  echo        Operacja nie jest uznana za zakonczona poprawnie.
  exit /b 1
)
echo [OK] Lokalny HEAD i GitHub HEAD sa identyczne.
echo ============================================================
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
  echo Uzyj opcji 13 ^(pull --ff-only^) i sprawdz projekt przed wyslaniem.
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
set "LATEST_X86_APK="
set "LATEST_VIDEO_APK="
set "LATEST_VIDEO_UNIVERSAL_APK="
for /f "delims=" %%F in ('dir /b /a-d /o-d "%BUILD_APK_DIR%\*arm64-v8a.apk" 2^>nul') do if not defined LATEST_APK set "LATEST_APK=%BUILD_APK_DIR%\%%F"
if not defined LATEST_APK for /f "delims=" %%F in ('dir /b /a-d /o-d "%MUSIC_APK_STORE%\*arm64-v8a.apk" 2^>nul') do if not defined LATEST_APK set "LATEST_APK=%MUSIC_APK_STORE%\%%F"
for /f "delims=" %%F in ('dir /b /a-d /o-d "%BUILD_APK_DIR%\*universal.apk" 2^>nul') do if not defined LATEST_UNIVERSAL_APK set "LATEST_UNIVERSAL_APK=%BUILD_APK_DIR%\%%F"
if not defined LATEST_UNIVERSAL_APK for /f "delims=" %%F in ('dir /b /a-d /o-d "%MUSIC_APK_STORE%\*universal.apk" 2^>nul') do if not defined LATEST_UNIVERSAL_APK set "LATEST_UNIVERSAL_APK=%MUSIC_APK_STORE%\%%F"
for /f "delims=" %%F in ('dir /b /a-d /o-d "%BUILD_APK_DIR%\*x86.apk" 2^>nul') do if not defined LATEST_X86_APK set "LATEST_X86_APK=%BUILD_APK_DIR%\%%F"
for /f "delims=" %%F in ('dir /b /a-d /o-d "%BUILD_VIDEO_APK_DIR%\*arm64-v8a.apk" 2^>nul') do if not defined LATEST_VIDEO_APK set "LATEST_VIDEO_APK=%BUILD_VIDEO_APK_DIR%\%%F"
if not defined LATEST_VIDEO_APK for /f "delims=" %%F in ('dir /b /a-d /o-d "%VIDEO_APK_STORE%\*arm64-v8a.apk" 2^>nul') do if not defined LATEST_VIDEO_APK set "LATEST_VIDEO_APK=%VIDEO_APK_STORE%\%%F"
for /f "delims=" %%F in ('dir /b /a-d /o-d "%BUILD_VIDEO_APK_DIR%\*universal.apk" 2^>nul') do if not defined LATEST_VIDEO_UNIVERSAL_APK set "LATEST_VIDEO_UNIVERSAL_APK=%BUILD_VIDEO_APK_DIR%\%%F"
if not defined LATEST_VIDEO_UNIVERSAL_APK for /f "delims=" %%F in ('dir /b /a-d /o-d "%VIDEO_APK_STORE%\*universal.apk" 2^>nul') do if not defined LATEST_VIDEO_UNIVERSAL_APK set "LATEST_VIDEO_UNIVERSAL_APK=%VIDEO_APK_STORE%\%%F"
exit /b 0

:RUN_MOBILE_BUILD_ONLY
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
echo.
echo [INFO] Buduje SmartTube Mobile Debug ^(arm64-v8a + universal + x86^)...
pushd "%SOURCE_DIR%"
call gradlew.bat :smarttubetv:assembleStmobileDebug --console=plain --no-daemon --max-workers=1 --no-parallel --project-cache-dir "!GRADLE_CACHE!"
set "BUILD_CODE=!errorlevel!"
popd
if not "!BUILD_CODE!"=="0" exit /b !BUILD_CODE!
call :FIND_LATEST_APK
if not defined LATEST_APK (
  echo [BLAD] Build zakonczony, ale nie znaleziono APK arm64-v8a w:
  echo        %BUILD_APK_DIR%
  exit /b 1
)
echo [OK] Mobile arm64    : !LATEST_APK!
if defined LATEST_UNIVERSAL_APK echo [OK] Mobile universal: !LATEST_UNIVERSAL_APK!
if defined LATEST_X86_APK echo [OK] Mobile x86      : !LATEST_X86_APK!
exit /b 0

:PREPARE_ADB
set "ADB_EXE="
if defined ANDROID_SDK_ROOT if exist "%ANDROID_SDK_ROOT%\platform-tools\adb.exe" set "ADB_EXE=%ANDROID_SDK_ROOT%\platform-tools\adb.exe"
if not defined ADB_EXE if defined ANDROID_HOME if exist "%ANDROID_HOME%\platform-tools\adb.exe" set "ADB_EXE=%ANDROID_HOME%\platform-tools\adb.exe"
if not defined ADB_EXE if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" set "ADB_EXE=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if not defined ADB_EXE for /f "delims=" %%A in ('where adb 2^>nul') do if not defined ADB_EXE set "ADB_EXE=%%A"
if not defined ADB_EXE (
  echo [BLAD] Nie znaleziono Android SDK platform-tools\adb.exe.
  echo Zainstaluj Android SDK Platform-Tools w Android Studio SDK Manager.
  exit /b 1
)
for %%A in ("!ADB_EXE!") do set "ADB_PLATFORM_TOOLS=%%~dpA"
set "PATH=!ADB_PLATFORM_TOOLS!;!PATH!"
echo [OK] ADB: !ADB_EXE!
"!ADB_EXE!" version | findstr /B /C:"Android Debug Bridge" /C:"Version"
exit /b 0

:AUTHORIZE_AND_SELECT_PHONE
echo.
echo ============================================================
echo   AUTORYZACJA TELEFONU ADB / DEBUGOWANIE USB
echo ============================================================
echo ADB nie moze samo wlaczyc trybu programisty ani Debugowania USB.
echo Jezeli Debugowanie USB jest wlaczone, telefon powinien pokazac komunikat RSA.
echo.
echo Na telefonie:
echo  1. Odblokuj ekran.
echo  2. Zaznacz "Zawsze zezwalaj z tego komputera".
echo  3. Nacisnij "Zezwalaj".
echo.
echo Gdy brak komunikatu: Opcje programisty ^> Cofnij autoryzacje debugowania USB,
echo wylacz/wlacz Debugowanie USB i ponownie podlacz kabel danych.
echo ============================================================
"!ADB_EXE!" kill-server >nul 2>&1
"!ADB_EXE!" start-server >nul 2>&1
"!ADB_EXE!" devices -l
set /a ADB_WAITED=0
:PHONE_WAIT_LOOP
for /L %%N in (1,1,99) do set "PHONE_SERIAL_%%N="
set /a PHONE_COUNT=0
set /a PHONE_UNAUTHORIZED=0
for /f "skip=1 tokens=1,2" %%A in ('"!ADB_EXE!" devices 2^>nul') do (
  set "SCAN_SERIAL=%%A"
  set "SCAN_STATE=%%B"
  echo !SCAN_SERIAL! | findstr /B /I /C:"emulator-" >nul
  if errorlevel 1 (
    if /I "!SCAN_STATE!"=="device" (
      set /a PHONE_COUNT+=1
      set "PHONE_SERIAL_!PHONE_COUNT!=!SCAN_SERIAL!"
    )
    if /I "!SCAN_STATE!"=="unauthorized" set /a PHONE_UNAUTHORIZED+=1
  )
)
if !PHONE_COUNT! GTR 0 goto SELECT_AUTHORIZED_PHONE
if !ADB_WAITED! GEQ !ADB_WAIT_SECONDS! (
  echo.
  echo [BLAD] Brak autoryzowanego telefonu po !ADB_WAIT_SECONDS! sekundach.
  if !PHONE_UNAUTHORIZED! GTR 0 echo Telefon nadal ma status UNAUTHORIZED - zatwierdz komunikat RSA na ekranie.
  "!ADB_EXE!" devices -l
  exit /b 1
)
if !PHONE_UNAUTHORIZED! GTR 0 (
  echo [OCZEKIWANIE] Telefon ma status UNAUTHORIZED. Zatwierdz komunikat RSA...
) else (
  echo [OCZEKIWANIE] Telefon nie jest jeszcze widoczny. Sprawdz kabel i Debugowanie USB...
)
timeout /t 2 /nobreak >nul
set /a ADB_WAITED+=2
goto PHONE_WAIT_LOOP

:SELECT_AUTHORIZED_PHONE
set "SELECTED_DEVICE="
if !PHONE_COUNT! EQU 1 (
  set "SELECTED_DEVICE=!PHONE_SERIAL_1!"
  goto READ_SELECTED_PHONE
)
echo.
echo Autoryzowane telefony:
for /L %%N in (1,1,!PHONE_COUNT!) do (
  set "TMP_SERIAL=!PHONE_SERIAL_%%N!"
  set "TMP_MODEL="
  for /f "delims=" %%M in ('"!ADB_EXE!" -s "!TMP_SERIAL!" shell getprop ro.product.model 2^>nul') do if not defined TMP_MODEL set "TMP_MODEL=%%M"
  echo   %%N - !TMP_SERIAL!  !TMP_MODEL!
)
set "PHONE_PICK="
set /p "PHONE_PICK=Wybierz telefon [1-!PHONE_COUNT!] (Enter = 1): "
if not defined PHONE_PICK set "PHONE_PICK=1"
for /L %%N in (1,1,!PHONE_COUNT!) do if "!PHONE_PICK!"=="%%N" set "SELECTED_DEVICE=!PHONE_SERIAL_%%N!"
if not defined SELECTED_DEVICE (
  echo [BLAD] Niepoprawny numer telefonu.
  exit /b 1
)

:READ_SELECTED_PHONE
set "SELECTED_MODEL="
set "SELECTED_ABI="
for /f "delims=" %%M in ('"!ADB_EXE!" -s "!SELECTED_DEVICE!" shell getprop ro.product.model 2^>nul') do if not defined SELECTED_MODEL set "SELECTED_MODEL=%%M"
for /f "delims=" %%M in ('"!ADB_EXE!" -s "!SELECTED_DEVICE!" shell getprop ro.product.cpu.abi 2^>nul') do if not defined SELECTED_ABI set "SELECTED_ABI=%%M"
if not defined SELECTED_MODEL set "SELECTED_MODEL=Android"
if not defined SELECTED_ABI set "SELECTED_ABI=?"
echo [OK] Telefon: !SELECTED_MODEL! [!SELECTED_DEVICE!] ABI=!SELECTED_ABI!
exit /b 0

:SELECT_EMULATOR
"!ADB_EXE!" start-server >nul 2>&1
for /L %%N in (1,1,99) do set "EMU_SERIAL_%%N="
set /a EMU_COUNT=0
for /f "skip=1 tokens=1,2" %%A in ('"!ADB_EXE!" devices 2^>nul') do (
  if /I "%%B"=="device" (
    echo %%A | findstr /B /I /C:"emulator-" >nul
    if not errorlevel 1 (
      set /a EMU_COUNT+=1
      set "EMU_SERIAL_!EMU_COUNT!=%%A"
    )
  )
)
if !EMU_COUNT! EQU 0 (
  echo [BLAD] Brak uruchomionego emulatora. ADB widzi:
  "!ADB_EXE!" devices -l
  echo Uruchom AVD w Android Studio Device Manager i sprobuj ponownie.
  exit /b 1
)
echo.
echo Dostepne emulatory:
for /L %%N in (1,1,!EMU_COUNT!) do (
  set "TMP_SERIAL=!EMU_SERIAL_%%N!"
  set "TMP_MODEL="
  set "TMP_AVD="
  set "TMP_ABI="
  for /f "delims=" %%M in ('"!ADB_EXE!" -s "!TMP_SERIAL!" shell getprop ro.product.model 2^>nul') do if not defined TMP_MODEL set "TMP_MODEL=%%M"
  for /f "delims=" %%M in ('"!ADB_EXE!" -s "!TMP_SERIAL!" shell getprop ro.boot.qemu.avd_name 2^>nul') do if not defined TMP_AVD set "TMP_AVD=%%M"
  for /f "delims=" %%M in ('"!ADB_EXE!" -s "!TMP_SERIAL!" shell getprop ro.product.cpu.abi 2^>nul') do if not defined TMP_ABI set "TMP_ABI=%%M"
  if not defined TMP_MODEL set "TMP_MODEL=Android Emulator"
  if not defined TMP_AVD set "TMP_AVD=AVD"
  echo   %%N - !TMP_SERIAL! [!TMP_AVD!] !TMP_MODEL! ABI=!TMP_ABI!
)
set "SELECTED_DEVICE="
if !EMU_COUNT! EQU 1 (
  set "SELECTED_DEVICE=!EMU_SERIAL_1!"
) else (
  set "EMU_PICK="
  set /p "EMU_PICK=Wybierz emulator [1-!EMU_COUNT!] (Enter = 1): "
  if not defined EMU_PICK set "EMU_PICK=1"
  for /L %%N in (1,1,!EMU_COUNT!) do if "!EMU_PICK!"=="%%N" set "SELECTED_DEVICE=!EMU_SERIAL_%%N!"
)
if not defined SELECTED_DEVICE (
  echo [BLAD] Nie wybrano poprawnego emulatora.
  exit /b 1
)
set "SELECTED_ABI="
for /f "delims=" %%M in ('"!ADB_EXE!" -s "!SELECTED_DEVICE!" shell getprop ro.product.cpu.abi 2^>nul') do if not defined SELECTED_ABI set "SELECTED_ABI=%%M"
if not defined SELECTED_ABI set "SELECTED_ABI=?"
echo [OK] Emulator: !SELECTED_DEVICE! ABI=!SELECTED_ABI!
exit /b 0

:SELECT_SMARTUBE_APK_FOR_DEVICE
set "APK_DEVICE=%~1"
set "TARGET_APK="
set "DEVICE_ABI="
call :FIND_LATEST_APK
for /f "delims=" %%M in ('"!ADB_EXE!" -s "!APK_DEVICE!" shell getprop ro.product.cpu.abi 2^>nul') do if not defined DEVICE_ABI set "DEVICE_ABI=%%M"
if not defined DEVICE_ABI set "DEVICE_ABI=?"
echo [INFO] ABI wybranego urzadzenia: !DEVICE_ABI!
echo !DEVICE_ABI! | findstr /I /C:"arm64" >nul
if not errorlevel 1 if defined LATEST_APK set "TARGET_APK=!LATEST_APK!"
if not defined TARGET_APK (
  echo !DEVICE_ABI! | findstr /I /C:"x86" >nul
  if not errorlevel 1 if defined LATEST_X86_APK set "TARGET_APK=!LATEST_X86_APK!"
)
if not defined TARGET_APK if defined LATEST_UNIVERSAL_APK set "TARGET_APK=!LATEST_UNIVERSAL_APK!"
if not defined TARGET_APK if defined LATEST_APK set "TARGET_APK=!LATEST_APK!"
if not defined TARGET_APK (
  echo [BLAD] Nie znaleziono pasujacego APK w:
  echo        %BUILD_APK_DIR%
  echo Najpierw zbuduj SmartTube Mobile opcja 9 albo pelny zestaw opcja 14.
  exit /b 1
)
echo [OK] APK dobrany do urzadzenia: !TARGET_APK!
exit /b 0

:INSTALL_AND_LAUNCH
if not exist "!TARGET_APK!" (
  echo [BLAD] Brak APK: !TARGET_APK!
  exit /b 1
)
echo.
echo [INFO] Instalacja na !TARGET_DEVICE!:
echo        !TARGET_APK!
set "ADB_INSTALL_LOG=%TEMP%\smarttube-adb-install.txt"
"!ADB_EXE!" -s "!TARGET_DEVICE!" install -r -d "!TARGET_APK!" >"!ADB_INSTALL_LOG!" 2>&1
set "ADB_INSTALL_RC=!ERRORLEVEL!"
type "!ADB_INSTALL_LOG!"
if not "!ADB_INSTALL_RC!"=="0" (
  findstr /I /C:"INSTALL_FAILED_UPDATE_INCOMPATIBLE" /C:"signatures do not match" "!ADB_INSTALL_LOG!" >nul 2>&1
  if errorlevel 1 exit /b 1
  echo.
  echo [UWAGA] Istniejaca instalacja !TARGET_PACKAGE! ma inny podpis.
  echo Jej usuniecie skasuje lokalne dane tej aplikacji na wybranym urzadzeniu.
  set "UNINSTALL_CONFIRM="
  set /p "UNINSTALL_CONFIRM=Usunac tylko !TARGET_PACKAGE! i kontynuowac? [T/N]: "
  if /I not "!UNINSTALL_CONFIRM!"=="T" exit /b 1
  "!ADB_EXE!" -s "!TARGET_DEVICE!" uninstall "!TARGET_PACKAGE!" || exit /b 1
  "!ADB_EXE!" -s "!TARGET_DEVICE!" install -d "!TARGET_APK!" || exit /b 1
)
echo [INFO] Uruchamianie !TARGET_PACKAGE!...
"!ADB_EXE!" -s "!TARGET_DEVICE!" shell am force-stop "!TARGET_PACKAGE!" >nul 2>&1
"!ADB_EXE!" -s "!TARGET_DEVICE!" shell monkey -p "!TARGET_PACKAGE!" -c android.intent.category.LAUNCHER 1 >nul 2>&1
if errorlevel 1 (
  echo [BLAD] Nie udalo sie uruchomic launchera pakietu !TARGET_PACKAGE!.
  exit /b 1
)
set "APP_PID="
set "PID_TMP=%TEMP%\smarttube-pid.txt"
for /L %%W in (1,1,10) do (
  if not defined APP_PID (
    "!ADB_EXE!" -s "!TARGET_DEVICE!" shell pidof "!TARGET_PACKAGE!" >"!PID_TMP!" 2>nul
    set /p "APP_PID=" <"!PID_TMP!"
    if not defined APP_PID timeout /t 1 /nobreak >nul
  )
)
del /q "!PID_TMP!" >nul 2>&1
if defined APP_PID (
  echo [OK] Aplikacja dziala. PID: !APP_PID!
) else (
  echo [UWAGA] Instalacja sie udala, ale PID nie zostal wykryty po 10 sekundach.
  echo         Sprawdz ekran urzadzenia - aplikacja mogla przejsc w tlo.
)
"!ADB_EXE!" -s "!TARGET_DEVICE!" shell dumpsys package "!TARGET_PACKAGE!" 2>nul | findstr /I "versionName versionCode"
exit /b 0

:SHOW_ADB_DIAGNOSTICS
echo.
echo ============================================================
echo   DIAGNOSTYKA ADB - SMARTTUBE MOBILE
echo ============================================================
"!ADB_EXE!" start-server >nul 2>&1
"!ADB_EXE!" devices -l
set "DIAG_ANY="
for /f "skip=1 tokens=1,2" %%A in ('"!ADB_EXE!" devices 2^>nul') do if not "%%A"=="" (
  set "DIAG_ANY=1"
  echo.
  echo Urzadzenie: %%A  status: %%B
  if /I "%%B"=="unauthorized" echo   Odblokuj telefon i zatwierdz "Zezwalaj na debugowanie USB".
  if /I "%%B"=="device" call :DIAGNOSE_ONE_DEVICE "%%A"
)
if not defined DIAG_ANY echo [INFO] ADB nie widzi zadnego urzadzenia.
echo.
call :FIND_LATEST_APK
echo Lokalne APK SmartTube Mobile:
if defined LATEST_APK echo   arm64    : !LATEST_APK!
if defined LATEST_X86_APK echo   x86      : !LATEST_X86_APK!
if defined LATEST_UNIVERSAL_APK echo   universal: !LATEST_UNIVERSAL_APK!
exit /b 0

:DIAGNOSE_ONE_DEVICE
set "DIAG_SERIAL=%~1"
set "DIAG_MODEL="
set "DIAG_ANDROID="
set "DIAG_API="
set "DIAG_ABI="
for /f "delims=" %%M in ('"!ADB_EXE!" -s "!DIAG_SERIAL!" shell getprop ro.product.model 2^>nul') do if not defined DIAG_MODEL set "DIAG_MODEL=%%M"
for /f "delims=" %%M in ('"!ADB_EXE!" -s "!DIAG_SERIAL!" shell getprop ro.build.version.release 2^>nul') do if not defined DIAG_ANDROID set "DIAG_ANDROID=%%M"
for /f "delims=" %%M in ('"!ADB_EXE!" -s "!DIAG_SERIAL!" shell getprop ro.build.version.sdk 2^>nul') do if not defined DIAG_API set "DIAG_API=%%M"
for /f "delims=" %%M in ('"!ADB_EXE!" -s "!DIAG_SERIAL!" shell getprop ro.product.cpu.abi 2^>nul') do if not defined DIAG_ABI set "DIAG_ABI=%%M"
if not defined DIAG_MODEL set "DIAG_MODEL=Android"
if not defined DIAG_ANDROID set "DIAG_ANDROID=?"
if not defined DIAG_API set "DIAG_API=?"
if not defined DIAG_ABI set "DIAG_ABI=?"
echo   Model: !DIAG_MODEL!  Android: !DIAG_ANDROID!  API: !DIAG_API!  ABI: !DIAG_ABI!
echo   SmartTube Mobile ^(%SMARTUBE_MOBILE_PACKAGE%^):
"!ADB_EXE!" -s "!DIAG_SERIAL!" shell dumpsys package "%SMARTUBE_MOBILE_PACKAGE%" 2>nul | findstr /I "versionName versionCode"
if errorlevel 1 echo     NIE ZAINSTALOWANO
echo   SmartTube Video EXP ^(%SMARTUBE_VIDEO_PACKAGE%^):
"!ADB_EXE!" -s "!DIAG_SERIAL!" shell dumpsys package "%SMARTUBE_VIDEO_PACKAGE%" 2>nul | findstr /I "versionName versionCode"
if errorlevel 1 echo     NIE ZAINSTALOWANO
exit /b 0

:COMMAND_FAILED
echo.
echo [BLAD] Operacja nie zostala zakonczona.
echo Sprawdz komunikaty powyzej. Helper nie wykonuje automatycznego resetu ani kasowania repo.
echo.
pause
goto MENU
