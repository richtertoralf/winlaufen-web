<#
.SYNOPSIS
    WinLaufen Web Setup für Windows 11.

.DESCRIPTION
    Der Installer fragt ausschließlich das Installationsprofil ab. Es werden zu
    keinem Zeitpunkt WinLaufen-Adressen, Target-Adressen, Hostnamen, URLs oder
    WSS-Ziele abgefragt: diese Werte gehören in die spätere Runtime-Konfiguration
    über Bridge Control.

    Unterstützte Profile unter Windows: AllInOne und BridgeOnly.
    Presentation Node wird unter Windows bewusst nicht unterstützt; dafür Linux
    verwenden.

    Die Dienste laufen als geplante Aufgaben (Scheduled Tasks) mit dem Trigger
    "Beim Systemstart" unter dem Konto LocalSystem. Damit starten sie ohne
    Anmeldung im Hintergrund und benötigen kein offenes Konsolenfenster. Diese
    Lösung ist bewusst gewählt, weil sie vollständig in Windows enthalten ist und
    keine zusätzlichen Service-Wrapper mit eigener Lizenz- und Distributionsfrage
    erfordert.

.PARAMETER Profile
    AllInOne (Standard) oder BridgeOnly.

.PARAMETER DistPath
    Verzeichnis der Distribution (mit lib\ und optional runtime\).
    Standard: das übergeordnete Verzeichnis dieses Skripts.

.PARAMETER StagingRoot
    Nur für Tests: installiert in ein Verzeichnis statt in die Systempfade und
    legt keine geplanten Aufgaben an.

.EXAMPLE
    .\Install-WinLaufenWeb.ps1
    .\Install-WinLaufenWeb.ps1 -Profile BridgeOnly
#>
[CmdletBinding()]
param(
    [ValidateSet('AllInOne', 'BridgeOnly')]
    [string]$Profile,

    [string]$DistPath,

    [string]$StagingRoot,

    [switch]$SkipTasks
)

$ErrorActionPreference = 'Stop'

# ------------------------------------------------------------ Kenngrößen
# Diese Werte stammen aus installer/common/dist-manifest.env und damit aus dem
# Anwendungscode. Sie dürfen hier nicht abweichen.

$ProductName          = 'WinLaufen Web'
$JavaRelease          = 25
$BridgeJar            = 'winlaufen-web-bridge.jar'
$LiveJar              = 'winlaufen-web-live-server.jar'
$SourcePort           = 4444
$DefaultSourceHost    = '127.0.0.1'
$ControlBind          = '127.0.0.1'
$ControlPort          = 8090
$LiveHttpBind         = '0.0.0.0'
$LiveHttpPort         = 8080
$LiveWsBind           = '0.0.0.0'
$LiveWsPort           = 8081
$LiveChannel          = 'local'
$IngestPathPrefix     = '/bridge/v1/channels/'
$DefaultSecret        = 'local-development-secret'
$BridgeConfigProperty = 'winlaufen.bridge.config'

$BridgeTaskName = 'WinLaufen Web Bridge'
$LiveTaskName   = 'WinLaufen Web Live Server'

function Write-Note { param([string]$Text) Write-Host "  $Text" }

function Get-StagedPath {
    param([string]$Path)
    if ([string]::IsNullOrWhiteSpace($StagingRoot)) { return $Path }
    $qualifier = [System.IO.Path]::GetPathRoot($Path)
    $relative = $Path.Substring($qualifier.Length)
    return (Join-Path $StagingRoot $relative)
}

function Test-Administrator {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object Security.Principal.WindowsPrincipal($identity)
    return $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

function Resolve-JavaExecutable {
    param([string]$DistRoot, [string]$InstalledPrefix)

    # Bevorzugt eine mitgelieferte jlink-Runtime.
    $bundled = Join-Path $DistRoot 'runtime\bin\javaw.exe'
    if (Test-Path -LiteralPath $bundled) {
        return (Join-Path $InstalledPrefix 'runtime\bin\javaw.exe')
    }

    # Sonst System-Java. javaw.exe startet ohne Konsolenfenster.
    $candidate = Get-Command 'javaw.exe' -ErrorAction SilentlyContinue
    if (-not $candidate) { $candidate = Get-Command 'java.exe' -ErrorAction SilentlyContinue }
    if (-not $candidate) { return $null }

    $output = & $candidate.Source '-XshowSettings:properties' '-version' 2>&1
    $match = $output | Select-String -Pattern 'java\.specification\.version\s*=\s*(\d+)'
    if (-not $match) { return $null }
    $major = [int]$match.Matches[0].Groups[1].Value
    if ($major -lt $JavaRelease) { return $null }
    return $candidate.Source
}

# ------------------------------------------------------------ Profilauswahl

if (-not $Profile) {
    Write-Host ""
    Write-Host "$ProductName Setup"
    Write-Host ""
    Write-Host "Installationsprofil:"
    Write-Host ""
    Write-Host "  [1] All-in-One"
    Write-Host "      Bridge + Live Server"
    Write-Host "      Empfohlen für Installation direkt auf dem WinLaufen-PC"
    Write-Host ""
    Write-Host "  [2] Bridge only"
    Write-Host "      Nur WinLaufen Bridge"
    Write-Host ""
    Write-Host "  Presentation Node wird unter Windows nicht unterstützt."
    Write-Host "  Dafür bitte die Linux-Installation verwenden."
    Write-Host ""
    $answer = Read-Host "Auswahl [1]"
    switch ($answer) {
        ''  { $Profile = 'AllInOne' }
        '1' { $Profile = 'AllInOne' }
        '2' { $Profile = 'BridgeOnly' }
        default { throw "Ungültige Auswahl: $answer" }
    }
}

$installBridge = $true
$installLive = ($Profile -eq 'AllInOne')

# ------------------------------------------------------------ Vorbedingungen

if (-not $DistPath) {
    $DistPath = Split-Path -Parent $PSScriptRoot
}
$DistPath = (Resolve-Path -LiteralPath $DistPath).Path

$libDir = Join-Path $DistPath 'lib'
if (Test-Path -LiteralPath (Join-Path $libDir $BridgeJar)) {
    $bridgeSource = Join-Path $libDir $BridgeJar
    $liveSource = Join-Path $libDir $LiveJar
} else {
    # Direkt aus dem Repository heraus (Entwicklungsfall).
    $bridgeSource = Join-Path $DistPath "bridge\target\$BridgeJar"
    $liveSource = Join-Path $DistPath "live-server\target\$LiveJar"
}

if ($installBridge -and -not (Test-Path -LiteralPath $bridgeSource)) {
    throw "$bridgeSource fehlt. Zuerst 'mvn package' oder installer\common\build-dist.ps1 ausführen."
}
if ($installLive -and -not (Test-Path -LiteralPath $liveSource)) {
    throw "$liveSource fehlt. Zuerst 'mvn package' oder installer\common\build-dist.ps1 ausführen."
}

$InstallPrefix = Join-Path $env:ProgramFiles 'WinLaufen Web'
$ConfigDir     = Join-Path $env:ProgramData 'WinLaufen Web'
$StateDir      = Join-Path $ConfigDir 'state'
$LogDir        = Join-Path $ConfigDir 'logs'

if ([string]::IsNullOrWhiteSpace($StagingRoot)) {
    if (-not (Test-Administrator)) {
        throw "Bitte PowerShell als Administrator ausführen."
    }
}

$javaExe = Resolve-JavaExecutable -DistRoot $DistPath -InstalledPrefix $InstallPrefix
if (-not $javaExe) {
    throw @"
Keine passende Java-Runtime gefunden (benötigt Java >= $JavaRelease).
Entweder ein JDK/JRE >= $JavaRelease installieren oder eine Distribution mit
gebündelter Runtime verwenden: installer\common\build-dist.ps1 -WithRuntime
"@
}

Write-Host ""
Write-Host "== Installiere Profil: $Profile =="
Write-Note "Java:          $javaExe"
Write-Note "Programm:      $(Get-StagedPath $InstallPrefix)"
Write-Note "Konfiguration: $(Get-StagedPath $ConfigDir)"

# ------------------------------------------------------------ Dateien

foreach ($dir in @((Join-Path $InstallPrefix 'lib'), $ConfigDir, $StateDir, $LogDir)) {
    New-Item -ItemType Directory -Force -Path (Get-StagedPath $dir) | Out-Null
}

if ($installBridge) {
    Copy-Item -LiteralPath $bridgeSource -Destination (Get-StagedPath (Join-Path $InstallPrefix "lib\$BridgeJar")) -Force
    Write-Note "Bridge-Artefakt installiert"
}
if ($installLive) {
    Copy-Item -LiteralPath $liveSource -Destination (Get-StagedPath (Join-Path $InstallPrefix "lib\$LiveJar")) -Force
    Write-Note "Live-Server-Artefakt installiert"
}

$runtimeSource = Join-Path $DistPath 'runtime'
if (Test-Path -LiteralPath $runtimeSource) {
    $runtimeTarget = Get-StagedPath (Join-Path $InstallPrefix 'runtime')
    if (Test-Path -LiteralPath $runtimeTarget) { Remove-Item -LiteralPath $runtimeTarget -Recurse -Force }
    Copy-Item -LiteralPath $runtimeSource -Destination $runtimeTarget -Recurse -Force
    Write-Note "Gebündelte Java-Runtime installiert"
}

# ------------------------------------------------------------ Konfiguration
#
# Vorhandene Konfiguration wird niemals überschrieben. Defaults entstehen nur bei
# einer echten Erstinstallation, damit ein Upgrade eine bereits gepflegte
# WinLaufen-Adresse oder Target-Liste nicht zurücksetzt.

$bridgeConfig = Join-Path $ConfigDir 'bridge.properties'
$liveConfig   = Join-Path $ConfigDir 'live-server.properties'

if ($installBridge) {
    $stagedBridgeConfig = Get-StagedPath $bridgeConfig
    if (Test-Path -LiteralPath $stagedBridgeConfig) {
        Write-Note "Bestehende Bridge-Konfiguration beibehalten: $bridgeConfig"
    } else {
        if ($Profile -eq 'AllInOne') {
            # All-in-One: lokaler Live Server als reguläres Output Target.
            $content = @"
# $ProductName - Bridge (Profil: All-in-One)
# Erzeugt bei der Erstinstallation. Änderungen bitte über Bridge Control
# vornehmen: http://localhost:$ControlPort/
config.version=2
source.type=WINLAUFEN
source.host=$DefaultSourceHost
bridge.control.bind=$ControlBind
bridge.control.port=$ControlPort
outputs.count=1
outputs.0.id=local
outputs.0.type=LOCAL
outputs.0.enabled=true
outputs.0.endpoint=ws://127.0.0.1:$LiveWsPort$IngestPathPrefix$LiveChannel
outputs.0.channelId=$LiveChannel
outputs.0.secret=$DefaultSecret
presentation.showClub=true
presentation.showAssociation=true
presentation.showNation=false
presentation.showShooting=true
presentation.showMessages=false
"@
        } else {
            # Bridge only: gültig auch ohne Output Target.
            $content = @"
# $ProductName - Bridge (Profil: Bridge only)
# Erzeugt bei der Erstinstallation. WinLaufen-Adresse und Output Targets
# anschließend über Bridge Control pflegen: http://localhost:$ControlPort/
config.version=2
source.type=WINLAUFEN
source.host=$DefaultSourceHost
bridge.control.bind=$ControlBind
bridge.control.port=$ControlPort
outputs.count=0
presentation.showClub=true
presentation.showAssociation=true
presentation.showNation=false
presentation.showShooting=true
presentation.showMessages=false
"@
        }
        Set-Content -LiteralPath $stagedBridgeConfig -Value $content -Encoding UTF8
        Write-Note "Bridge-Standardkonfiguration erzeugt: $bridgeConfig"
    }
}

if ($installLive) {
    $stagedLiveConfig = Get-StagedPath $liveConfig
    if (Test-Path -LiteralPath $stagedLiveConfig) {
        Write-Note "Bestehende Live-Server-Konfiguration beibehalten: $liveConfig"
    } else {
        $content = @"
# $ProductName - Live Server
# Rein technische Deployment-Parameter. Keine Veranstalter-Konfiguration.
winlaufen.live.http.bind=$LiveHttpBind
winlaufen.live.http.port=$LiveHttpPort
winlaufen.live.websocket.bind=$LiveWsBind
winlaufen.live.websocket.port=$LiveWsPort
winlaufen.live.channel=$LiveChannel
winlaufen.live.secret=$DefaultSecret
"@
        Set-Content -LiteralPath $stagedLiveConfig -Value $content -Encoding UTF8
        Write-Note "Live-Server-Standardkonfiguration erzeugt: $liveConfig"
    }
}

# ------------------------------------------------------------ Startskripte
#
# Die geplanten Aufgaben rufen diese Skripte auf. Sie lesen die Konfiguration und
# setzen daraus die Systemproperties der Anwendung.

$bridgeLauncher = Join-Path $InstallPrefix 'start-bridge.cmd'
$liveLauncher   = Join-Path $InstallPrefix 'start-live-server.cmd'

if ($installBridge) {
    $cmd = @"
@echo off
rem $ProductName - Bridge. Wird von der geplanten Aufgaben-Instanz aufgerufen.
start "" /B "$javaExe" "-D$BridgeConfigProperty=$bridgeConfig" -jar "$InstallPrefix\lib\$BridgeJar"
"@
    Set-Content -LiteralPath (Get-StagedPath $bridgeLauncher) -Value $cmd -Encoding ASCII
}

if ($installLive) {
    # Der Live Server wird ausschließlich über Systemproperties konfiguriert.
    # Die Properties-Datei wird hier zu -D-Argumenten expandiert.
    $ps1 = Join-Path $InstallPrefix 'start-live-server.ps1'
    $launcherScript = @"
# $ProductName - Live Server Starter
`$config = @{}
Get-Content -LiteralPath '$liveConfig' | ForEach-Object {
    if (`$_ -match '^\s*([^#=]+)=(.*)$') { `$config[`$Matches[1].Trim()] = `$Matches[2].Trim() }
}
`$arguments = @()
foreach (`$key in `$config.Keys) { `$arguments += "-D`$key=`$(`$config[`$key])" }
`$arguments += '-jar'
`$arguments += '$InstallPrefix\lib\$LiveJar'
Start-Process -FilePath '$javaExe' -ArgumentList `$arguments -WindowStyle Hidden
"@
    Set-Content -LiteralPath (Get-StagedPath $ps1) -Value $launcherScript -Encoding UTF8

    $cmd = @"
@echo off
rem $ProductName - Live Server. Wird von der geplanten Aufgaben-Instanz aufgerufen.
powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "$ps1"
"@
    Set-Content -LiteralPath (Get-StagedPath $liveLauncher) -Value $cmd -Encoding ASCII
}

# ------------------------------------------------------------ Geplante Aufgaben

function Register-BackgroundTask {
    param([string]$TaskName, [string]$Launcher)

    $action = New-ScheduledTaskAction -Execute 'cmd.exe' -Argument "/c `"$Launcher`""
    $trigger = New-ScheduledTaskTrigger -AtStartup
    $principal = New-ScheduledTaskPrincipal -UserId 'SYSTEM' -LogonType ServiceAccount -RunLevel Highest
    $settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries `
        -StartWhenAvailable -RestartCount 3 -RestartInterval (New-TimeSpan -Minutes 1) `
        -ExecutionTimeLimit ([TimeSpan]::Zero)

    # Idempotent: eine vorhandene Aufgabe wird ersetzt, nicht dupliziert.
    Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false -ErrorAction SilentlyContinue
    Register-ScheduledTask -TaskName $TaskName -Action $action -Trigger $trigger `
        -Principal $principal -Settings $settings -Description "$ProductName" | Out-Null
    Start-ScheduledTask -TaskName $TaskName
    Write-Note "Hintergrunddienst eingerichtet und gestartet: $TaskName"
}

function Remove-BackgroundTask {
    param([string]$TaskName)
    if (Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue) {
        Stop-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
        Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false
        Write-Note "Nicht zum Profil gehörenden Dienst entfernt: $TaskName"
    }
}

if ([string]::IsNullOrWhiteSpace($StagingRoot) -and -not $SkipTasks) {
    if ($installBridge) { Register-BackgroundTask -TaskName $BridgeTaskName -Launcher $bridgeLauncher }
    if ($installLive)   { Register-BackgroundTask -TaskName $LiveTaskName -Launcher $liveLauncher }
    else                { Remove-BackgroundTask -TaskName $LiveTaskName }
}

# ------------------------------------------------------------ Abschlussmeldung

Write-Host ""
if ($Profile -eq 'AllInOne') {
    Write-Host @"
Installation erfolgreich.

$ProductName wurde als All-in-One-System installiert.

Standardmäßig wird WinLaufen auf diesem Computer unter
localhost:$SourcePort erwartet.

Wenn WinLaufen auf diesem Computer läuft, ist keine weitere
Netzwerkkonfiguration erforderlich.

Falls WinLaufen auf einem anderen Rechner läuft, ändern Sie
anschließend die WinLaufen-Adresse in Bridge Control.

  Bridge Control: http://localhost:$ControlPort/
  Web View:       http://localhost:$LiveHttpPort/
"@
} else {
    Write-Host @"
Installation erfolgreich.

Die WinLaufen Bridge wurde installiert.

Vor dem produktiven Einsatz in Bridge Control prüfen:

- WinLaufen-Adresse, falls WinLaufen auf einem anderen Rechner läuft
- mindestens ein Output Target eintragen

  Bridge Control: http://localhost:$ControlPort/
"@
}

if ($installLive) {
    Write-Host @"

Hinweis zur Sicherheit: Diese Version ist ein Prototyp für kontrollierte Netze
und verwendet ein bekanntes Ingest-Secret. Port $LiveWsPort darf nicht aus nicht
vertrauenswürdigen Netzen erreichbar sein. Details in README.md, Abschnitt
"Known prototype security limitation".
"@
}
