# Builds Music and Video EXP on GitHub Actions. This PC only waits and downloads the APKs.
param(
    [string]$Repo = "AbcITAndrzej/smarttube-aa",
    [string]$Workflow = "Build SmartTube AA APKs",
    [Parameter(Mandatory = $true)][string]$MusicDir,
    [Parameter(Mandatory = $true)][string]$VideoDir
)

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

function Fail([string]$Message) {
    Write-Host "[BLAD] $Message"
    exit 1
}

gh auth status --hostname github.com | Out-Host
if ($LASTEXITCODE -ne 0) {
    Fail "GitHub CLI nie jest zalogowany. Uzyj opcji 5 helpera."
}

git -C $Root fetch origin main
if ($LASTEXITCODE -ne 0) { Fail "Nie udalo sie pobrac stanu GitHub." }

$Branch = (git -C $Root branch --show-current).Trim()
$Head = (git -C $Root rev-parse HEAD).Trim()
$Origin = (git -C $Root rev-parse "origin/main").Trim()
if ($Branch -ne "main") { Fail "Aktywna galaz to $Branch, a build na GitHubie idzie z main." }
if ($Head -ne $Origin) { Fail "Lokalny main rozni sie od GitHub. Najpierw push albo pull. Ten komputer nie kompiluje." }

$Dirty = git -C $Root status --porcelain
if ($Dirty) { Fail "Sa nie wyslane zmiany. Najpierw opcja 7. GitHub buduje tylko to, co jest na main." }

$Started = [DateTime]::UtcNow.AddMinutes(-2)
Write-Host "Startuje kompilacje na GitHubie: $Workflow"
gh workflow run $Workflow --repo $Repo --ref main
if ($LASTEXITCODE -ne 0) { Fail "Nie udalo sie uruchomic workflow." }

$RunId = $null
$RunUrl = $null
for ($i = 0; $i -lt 40; $i++) {
    Start-Sleep -Seconds 3
    $Json = gh run list --repo $Repo --workflow $Workflow --limit 5 --json databaseId,createdAt,headSha,url,status
    if ($LASTEXITCODE -ne 0) { continue }
    $Runs = $Json | ConvertFrom-Json
    foreach ($Run in $Runs) {
        $Created = [DateTime]::Parse($Run.createdAt).ToUniversalTime()
        if ($Created -ge $Started -and $Run.headSha -eq $Head) {
            $RunId = [string]$Run.databaseId
            $RunUrl = [string]$Run.url
            break
        }
    }
    if ($RunId) { break }
}
if (-not $RunId) { Fail "GitHub nie pokazal nowego buildu." }

Write-Host "GitHub run: $RunUrl"
gh run watch $RunId --repo $Repo --exit-status
if ($LASTEXITCODE -ne 0) { Fail "Kompilacja na GitHubie nie powiodla sie: $RunUrl" }

$Dest = Join-Path $env:TEMP "smarttube-aa-apks-$RunId"
if (Test-Path -LiteralPath $Dest) { Remove-Item -LiteralPath $Dest -Recurse -Force }
New-Item -ItemType Directory -Path $Dest | Out-Null
gh run download $RunId --repo $Repo --name smarttube-aa-apks --dir $Dest
if ($LASTEXITCODE -ne 0) { Fail "Nie udalo sie pobrac APK z GitHuba: $RunUrl" }

New-Item -ItemType Directory -Force -Path $MusicDir | Out-Null
New-Item -ItemType Directory -Force -Path $VideoDir | Out-Null
$Apks = @(Get-ChildItem -LiteralPath $Dest -Recurse -Filter *.apk)
if ($Apks.Count -eq 0) { Fail "Archiwum GitHuba nie zawiera APK." }

foreach ($Apk in $Apks) {
    $Target = $MusicDir
    if ($Apk.Name -match "carvideo") { $Target = $VideoDir }
    Copy-Item -LiteralPath $Apk.FullName -Destination (Join-Path $Target $Apk.Name) -Force
}

$Required = @(
    @{ Dir = $MusicDir; Pattern = "*arm64-v8a.apk" },
    @{ Dir = $MusicDir; Pattern = "*universal.apk" },
    @{ Dir = $VideoDir; Pattern = "*arm64-v8a.apk" },
    @{ Dir = $VideoDir; Pattern = "*universal.apk" }
)
foreach ($Item in $Required) {
    $Found = @(Get-ChildItem -LiteralPath $Item.Dir -Filter $Item.Pattern -ErrorAction SilentlyContinue)
    if ($Found.Count -eq 0) { Fail "Brak $($Item.Pattern) w $($Item.Dir)" }
}

Write-Host "[OK] APK pobrane z GitHuba. Kompilacja nie szla na tym komputerze."
exit 0
