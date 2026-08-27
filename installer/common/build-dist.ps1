<#
.SYNOPSIS
    Baut ein Distributionsverzeichnis für WinLaufen Web unter Windows.

.DESCRIPTION
    Erzeugt dist\ mit lib\, installer\ und optional einer per jlink reduzierten
    Java-Runtime. Die Runtime ist immer plattformspezifisch: ein Windows-Build
    erzeugt eine Windows-Runtime. Ein Cross-Build wird bewusst nicht versucht.

.EXAMPLE
    .\build-dist.ps1
    .\build-dist.ps1 -WithRuntime
#>
[CmdletBinding()]
param(
    [string]$Output,
    [switch]$WithRuntime,
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'

$repositoryRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
if (-not $Output) { $Output = Join-Path $repositoryRoot 'dist' }

$BridgeJar = 'winlaufen-web-bridge.jar'
$LiveJar   = 'winlaufen-web-live-server.jar'
$JavaRelease = 25

if (-not $SkipBuild) {
    Write-Host "== Baue Artefakte (mvn package) =="
    Push-Location $repositoryRoot
    try { & mvn -B -q package; if ($LASTEXITCODE -ne 0) { throw "mvn package fehlgeschlagen" } }
    finally { Pop-Location }
}

$bridgeSource = Join-Path $repositoryRoot "bridge\target\$BridgeJar"
$liveSource   = Join-Path $repositoryRoot "live-server\target\$LiveJar"
foreach ($jar in @($bridgeSource, $liveSource)) {
    if (-not (Test-Path -LiteralPath $jar)) { throw "$jar fehlt. Zuerst 'mvn package' ausführen." }
}

Write-Host "== Erzeuge Distribution in $Output =="
if (Test-Path -LiteralPath $Output) { Remove-Item -LiteralPath $Output -Recurse -Force }
New-Item -ItemType Directory -Force -Path (Join-Path $Output 'lib') | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $Output 'installer') | Out-Null

Copy-Item -LiteralPath $bridgeSource, $liveSource -Destination (Join-Path $Output 'lib') -Force
foreach ($part in @('linux', 'windows', 'common')) {
    Copy-Item -LiteralPath (Join-Path $repositoryRoot "installer\$part") `
              -Destination (Join-Path $Output 'installer') -Recurse -Force
}

$version = 'unbekannt'
try {
    Push-Location $repositoryRoot
    $described = & git describe --always --dirty 2>$null
    if ($LASTEXITCODE -eq 0 -and $described) { $version = $described }
} catch { } finally { Pop-Location -ErrorAction SilentlyContinue }
Set-Content -LiteralPath (Join-Path $Output 'VERSION') -Value $version -Encoding ASCII

if ($WithRuntime) {
    Write-Host "== Erzeuge reduzierte Java-Runtime (jlink) =="
    if (-not (Get-Command 'jlink' -ErrorAction SilentlyContinue)) { throw "jlink nicht gefunden." }
    & jlink --add-modules java.base,java.logging,java.naming,java.xml,jdk.httpserver,jdk.crypto.ec `
            --strip-debug --no-header-files --no-man-pages --compress=zip-6 `
            --output (Join-Path $Output 'runtime')
    if ($LASTEXITCODE -ne 0) { throw "jlink fehlgeschlagen" }
}

Write-Host ""
Write-Host "Distribution fertig: $Output"
Write-Host "  Version:  $version"
if ($WithRuntime) { Write-Host "  Runtime:  gebündelt" }
else { Write-Host "  Runtime:  System-Java erforderlich (>= $JavaRelease)" }
