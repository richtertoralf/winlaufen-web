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
    Wurzel der Distribution (mit lib\ und optional runtime\) oder eines
    Source-Checkouts (mit pom.xml und bridge\target\).
    Standard: zwei Ebenen über diesem Skript, also das Verzeichnis, das
    installer\ enthält.

.PARAMETER StagingRoot
    Nur für Tests: installiert in ein Verzeichnis statt in die Systempfade und
    legt keine geplanten Aufgaben an.

.EXAMPLE
    .\dist\installer\windows\Install-WinLaufenWeb.ps1
    .\installer\windows\Install-WinLaufenWeb.ps1 -Profile BridgeOnly
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
$ControlBind          = '0.0.0.0'
$ControlPort          = 44442
$LiveHttpBind         = '0.0.0.0'
$LiveHttpPort         = 44440
$LiveWsBind           = '0.0.0.0'
$LiveWsPort           = 44441
$LiveChannel          = 'local'
$IngestPathPrefix     = '/bridge/v1/channels/'
$DefaultSecret        = 'local-development-secret'
$BridgeConfigProperty = 'winlaufen.bridge.config'

$BridgeTaskName = 'WinLaufen Web Bridge'
$LiveTaskName   = 'WinLaufen Web Live Server'
$FirewallGroup  = 'WinLaufen Web'
$FirewallRules  = @(
    [pscustomobject]@{ Name = 'WinLaufenWeb-HTTP-44440'; Port = 44440; Role = 'Live'; Display = 'WinLaufen Web - Web View / HTTP (TCP 44440)' },
    [pscustomobject]@{ Name = 'WinLaufenWeb-WebSocket-44441'; Port = 44441; Role = 'Live'; Display = 'WinLaufen Web - Live WebSocket / Bridge Ingest (TCP 44441)' },
    [pscustomobject]@{ Name = 'WinLaufenWeb-BridgeControl-44442'; Port = 44442; Role = 'Bridge'; Display = 'WinLaufen Web - Bridge Control (TCP 44442)' }
)

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

# Ermittelt die Java-Major-Version ausschließlich über den Konsolen-Launcher
# java.exe. javaw.exe ist ein GUI-Subsystem-Programm; ob es seine Ausgabe an
# umgeleitete Handles schreibt, ist nicht zugesichert. Auf Windows 11 mit Temurin
# 25 liefert javaw.exe beim Einsammeln in eine Variable keinen Treffer, obwohl
# dasselbe Kommando direkt in eine Pipeline geschrieben funktioniert. javaw.exe
# darf deshalb nie als Versionsprobe dienen, sondern nur als Startprogramm.
# @return die Major-Version oder 0, wenn sie nicht ermittelt werden konnte.
function Get-JavaMajorVersion {
    param([string]$JavaExe)

    if ([string]::IsNullOrWhiteSpace($JavaExe) -or
            -not (Test-Path -LiteralPath $JavaExe -PathType Leaf)) {
        return 0
    }
    try {
        $output = & $JavaExe '-XshowSettings:properties' '-version' 2>&1
    } catch {
        return 0
    }
    $match = [regex]::Match(($output | Out-String), 'java\.specification\.version\s*=\s*(\d+)')
    if (-not $match.Success) {
        return 0
    }
    return [int]$match.Groups[1].Value
}

# Wählt zu einer geprüften java.exe das Startprogramm aus DERSELBEN Installation.
# javaw.exe startet ohne Konsolenfenster und wird bevorzugt; fehlt es, bleibt es
# bei der geprüften java.exe. Es werden nie zwei Java-Installationen gemischt.
function Select-RuntimeLauncher {
    param([string]$JavaExe)

    $launcher = Join-Path (Split-Path -Parent $JavaExe) 'javaw.exe'
    if (Test-Path -LiteralPath $launcher -PathType Leaf) {
        return $launcher
    }
    return $JavaExe
}

# System-Java-Kandidaten in fester Reihenfolge: JAVA_HOME vor PATH, immer als
# Konsolen-Launcher. Pfade mit Leerzeichen bleiben unverändert, weil ausschließlich
# Join-Path und -LiteralPath verwendet werden.
function Get-SystemJavaCandidates {
    $candidates = @()
    if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
        $candidates += (Join-Path $env:JAVA_HOME 'bin\java.exe')
    }
    foreach ($command in @(Get-Command 'java.exe' -All -ErrorAction SilentlyContinue)) {
        if ($command.Source) {
            $candidates += $command.Source
        }
    }
    return ($candidates | Where-Object { $_ } | Select-Object -Unique)
}

function Resolve-JavaExecutable {
    param([string]$DistRoot, [string]$InstalledPrefix)

    # Eine mitgelieferte jlink-Runtime hat Vorrang, wird aber genauso geprüft wie
    # System-Java: die bloße Existenz eines Startprogramms genügt nicht.
    $bundledJava = Join-Path $DistRoot 'runtime\bin\java.exe'
    if (Test-Path -LiteralPath $bundledJava -PathType Leaf) {
        $bundledMajor = Get-JavaMajorVersion -JavaExe $bundledJava
        if ($bundledMajor -lt $JavaRelease) {
            throw @"
Die gebündelte Java-Runtime unter $DistRoot\runtime ist nicht verwendbar.
Gemeldete Java-Version: $(if ($bundledMajor -gt 0) { $bundledMajor } else { 'nicht ermittelbar' }); benötigt wird >= $JavaRelease.
Die Distribution neu bauen: installer\common\build-dist.ps1 -WithRuntime
"@
        }
        # Nach der Installation liegt dieselbe Runtime unter $InstalledPrefix.
        $bundledLauncher = Split-Path -Leaf (Select-RuntimeLauncher -JavaExe $bundledJava)
        return (Join-Path $InstalledPrefix "runtime\bin\$bundledLauncher")
    }

    foreach ($candidate in (Get-SystemJavaCandidates)) {
        if ((Get-JavaMajorVersion -JavaExe $candidate) -ge $JavaRelease) {
            return (Select-RuntimeLauncher -JavaExe $candidate)
        }
    }
    return $null
}

# ------------------------------------------------------------ Profilauswahl

if ([string]::IsNullOrWhiteSpace($StagingRoot) -and -not (Test-Administrator)) {
    throw "Administratorrechte fehlen. Bitte PowerShell als Administrator starten und den Installer erneut ausführen."
}

if (-not $Profile) {
    Write-Host ""
    Write-Host "$ProductName Setup"
    Write-Host ""
    Write-Host "Installationsprofil:"
    Write-Host ""
    Write-Host "  [1] All-in-One"
    Write-Host "      Bridge + Live Server"
    Write-Host "      Für einen Rechner im lokalen Netz, z. B. WinLaufen-PC,"
    Write-Host "      Sprecher-PC oder separater LAN-PC"
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

# Dieses Skript liegt in <Wurzel>\installer\windows. Die Wurzel liegt damit zwei
# Ebenen darüber - in einer gebauten Distribution ist das dist\, in einem
# Source-Checkout das Repository. Der Linux-Installer verwendet dieselbe Tiefe.
if (-not $DistPath) {
    $DistPath = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
}
$DistPath = (Resolve-Path -LiteralPath $DistPath).Path

# Bestimmt, aus welchem der beiden unterstützten Layouts installiert wird. Beide
# hängen an derselben Wurzel und werden an einer eindeutigen Marke erkannt statt
# geraten: eine gebaute Distribution besitzt lib\, ein Source-Checkout das
# Root-POM. Ein Release-Paket enthält immer lib\ und kann deshalb nie
# versehentlich als Source-Checkout behandelt werden.
function Resolve-ArtifactLayout {
    param([string]$Root)

    $libDir = Join-Path $Root 'lib'
    if (Test-Path -LiteralPath $libDir -PathType Container) {
        return [pscustomobject]@{
            Kind = 'Distribution'
            Bridge = Join-Path $libDir $BridgeJar
            Live = Join-Path $libDir $LiveJar
            Hint = 'installer\common\build-dist.ps1'
        }
    }
    if (Test-Path -LiteralPath (Join-Path $Root 'pom.xml') -PathType Leaf) {
        return [pscustomobject]@{
            Kind = 'SourceTree'
            Bridge = Join-Path $Root "bridge\target\$BridgeJar"
            Live = Join-Path $Root "live-server\target\$LiveJar"
            Hint = '.\mvnw.cmd package'
        }
    }
    throw @"
Unter $Root wurde weder eine gebaute Distribution noch ein Source-Checkout gefunden.

Erwartet wird entweder
  $libDir\$BridgeJar          aus installer\common\build-dist.ps1
oder
  $Root\pom.xml mit bridge\target\$BridgeJar   aus .\mvnw.cmd package

Der Installer erwartet sich selbst unter <Wurzel>\installer\windows\.
"@
}

$layout = Resolve-ArtifactLayout -Root $DistPath
$bridgeSource = $layout.Bridge
$liveSource = $layout.Live

if ($installBridge -and -not (Test-Path -LiteralPath $bridgeSource -PathType Leaf)) {
    throw "$bridgeSource fehlt (Layout: $($layout.Kind)). Zuerst $($layout.Hint) ausführen."
}
if ($installLive -and -not (Test-Path -LiteralPath $liveSource -PathType Leaf)) {
    throw "$liveSource fehlt (Layout: $($layout.Kind)). Zuerst $($layout.Hint) ausführen."
}

$InstallPrefix = Join-Path $env:ProgramFiles 'WinLaufen Web'
$ConfigDir     = Join-Path $env:ProgramData 'WinLaufen Web'
$StateDir      = Join-Path $ConfigDir 'state'
$LogDir        = Join-Path $ConfigDir 'logs'

function Stop-ExistingWinLaufenProcesses {
    if (-not [string]::IsNullOrWhiteSpace($StagingRoot)) { return }

    foreach ($taskName in @($BridgeTaskName, $LiveTaskName)) {
        if (Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue) {
            Stop-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
        }
    }
    Get-CimInstance Win32_Process -Filter "Name like '%java%'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -and $_.CommandLine -like "*$InstallPrefix*" } |
        ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
    Start-Sleep -Milliseconds 500
}

function Get-ListenerOwner {
    param([int]$Port)
    return Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue |
        Select-Object -First 1
}

function Test-ListenerOwnedByThisInstallation {
    param($Listener)
    if (-not $Listener -or -not $Listener.OwningProcess) { return $false }

    $owner = Get-CimInstance Win32_Process -Filter "ProcessId=$($Listener.OwningProcess)" `
        -ErrorAction SilentlyContinue | Select-Object -First 1
    return [bool]($owner -and $owner.CommandLine -and
        $owner.CommandLine -like "*$InstallPrefix*")
}

function Assert-ListenerPortAvailable {
    param([int]$Port, [string]$Purpose, [switch]$AllowInstalledListener)

    $listener = Get-ListenerOwner -Port $Port
    if (-not $listener) { return }
    if ($AllowInstalledListener -and (Test-ListenerOwnedByThisInstallation -Listener $listener)) {
        return
    }

    $processName = 'unbekannt'
    if ($listener.OwningProcess) {
        $process = Get-Process -Id $listener.OwningProcess -ErrorAction SilentlyContinue
        if ($process) { $processName = $process.ProcessName }
    }
    $serviceName = $null
    if ($listener.OwningProcess) {
        $service = Get-CimInstance Win32_Service -Filter "ProcessId=$($listener.OwningProcess)" `
            -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($service) { $serviceName = $service.Name }
    }

    $message = @"
TCP-Port $Port ist bereits belegt.

Benötigt für: $Purpose

Prozess:
  $processName (PID $($listener.OwningProcess))
"@
    if ($serviceName) {
        $message += "`nDienst:`n  $serviceName`n"
    }
    $message += "`nDie Installation wurde nicht erfolgreich abgeschlossen."
    throw $message
}

function Invoke-PortPreflight {
    param([switch]$AllowInstalledListeners)
    if ($installLive) {
        Assert-ListenerPortAvailable -Port $LiveHttpPort -Purpose 'WinLaufen Web View / HTTP' `
            -AllowInstalledListener:$AllowInstalledListeners
        Assert-ListenerPortAvailable -Port $LiveWsPort -Purpose 'Live WebSocket / Bridge Ingest' `
            -AllowInstalledListener:$AllowInstalledListeners
    }
    if ($installBridge) {
        Assert-ListenerPortAvailable -Port $ControlPort -Purpose 'Bridge Control' `
            -AllowInstalledListener:$AllowInstalledListeners
    }
}

function Get-RunningWinLaufenTasks {
    $running = @()
    if (-not [string]::IsNullOrWhiteSpace($StagingRoot)) { return $running }

    foreach ($taskName in @($BridgeTaskName, $LiveTaskName)) {
        $task = Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
        if ($task -and $task.State -eq 'Running') {
            $running += $taskName
        }
    }
    return $running
}

function Restore-PreviouslyRunningTasks {
    param([string[]]$TaskNames)

    foreach ($taskName in $TaskNames) {
        if (Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue) {
            try {
                Start-ScheduledTask -TaskName $taskName -ErrorAction Stop
                Write-Warning "Vorher laufende Aufgabe nach Installationsfehler erneut gestartet: $taskName"
            } catch {
                Write-Warning "Vorher laufende Aufgabe konnte nicht erneut gestartet werden: $taskName ($($_.Exception.Message))"
            }
        } else {
            Write-Warning "Vorher laufende Aufgabe ist nach Installationsfehler nicht mehr registriert: $taskName"
        }
    }
}

function Assert-ConfigurationPrerequisites {
    $stagedConfigDir = Get-StagedPath $ConfigDir
    if ((Test-Path -LiteralPath $stagedConfigDir) -and
            -not (Test-Path -LiteralPath $stagedConfigDir -PathType Container)) {
        throw "Konfigurationspfad ist kein Verzeichnis: $ConfigDir"
    }

    foreach ($configPath in @(
            (Get-StagedPath (Join-Path $ConfigDir 'bridge.properties')),
            (Get-StagedPath (Join-Path $ConfigDir 'live-server.properties')))) {
        if (-not (Test-Path -LiteralPath $configPath)) { continue }
        if (-not (Test-Path -LiteralPath $configPath -PathType Leaf)) {
            throw "Konfigurationspfad ist keine Datei: $configPath"
        }
        $stream = $null
        try {
            $stream = [System.IO.File]::Open(
                $configPath,
                [System.IO.FileMode]::Open,
                [System.IO.FileAccess]::Read,
                [System.IO.FileShare]::ReadWrite
            )
        } finally {
            if ($stream) { $stream.Dispose() }
        }
    }
}

function Sync-WindowsFirewallRules {
    if (-not [string]::IsNullOrWhiteSpace($StagingRoot)) { return }

    foreach ($rule in $FirewallRules) {
        Get-NetFirewallRule -Name $rule.Name -ErrorAction SilentlyContinue |
            Remove-NetFirewallRule -ErrorAction Stop
    }
    foreach ($rule in $FirewallRules) {
        $needed = ($rule.Role -eq 'Bridge' -and $installBridge) -or
                  ($rule.Role -eq 'Live' -and $installLive)
        if (-not $needed) { continue }
        New-NetFirewallRule -Name $rule.Name -DisplayName $rule.Display `
            -Group $FirewallGroup -Enabled True -Direction Inbound -Action Allow `
            -Protocol TCP -LocalPort $rule.Port -Profile Private,Domain | Out-Null
        Write-Note "Windows-Firewallregel eingerichtet: $($rule.Display) (Private, Domain)"
    }
}

$javaExe = Resolve-JavaExecutable -DistRoot $DistPath -InstalledPrefix $InstallPrefix
if (-not $javaExe) {
    $searched = (Get-SystemJavaCandidates) -join "`n  "
    throw @"
Keine passende Java-Runtime gefunden (benötigt Java >= $JavaRelease).

Geprüft wurden (jeweils java.exe, nicht javaw.exe):
  $(if ($searched) { $searched } else { '(weder JAVA_HOME noch java.exe im PATH)' })

Entweder ein JDK/JRE >= $JavaRelease installieren und JAVA_HOME bzw. PATH setzen
oder eine Distribution mit gebündelter Runtime verwenden:
installer\common\build-dist.ps1 -WithRuntime
"@
}

# Fremde Portbelegungen und alle übrigen gefahrlosen Voraussetzungen werden
# geprüft, solange eine vorhandene Installation noch läuft. Eigene Listener
# sind in diesem ersten Preflight zulässig. TCP 4444 ist ein entferntes Ziel.
Assert-ConfigurationPrerequisites
Invoke-PortPreflight -AllowInstalledListeners
$previouslyRunningTasks = @(Get-RunningWinLaufenTasks)
$installationWasStopped = $false
$localRuntimeValidated = $false
$bridgeDiagnostic = $null

try {
    Stop-ExistingWinLaufenProcesses
    $installationWasStopped = $true
    # Jetzt müssen auch die vorher eigenen Listener tatsächlich freigegeben sein.
    Invoke-PortPreflight

Write-Host ""
Write-Host "== Installiere Profil: $Profile =="
Write-Note "Quelle:        $DistPath ($($layout.Kind))"
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

function Update-PropertiesLines {
    param([string]$Path, [scriptblock]$Transform)

    $bytes = [System.IO.File]::ReadAllBytes($Path)
    $hasUtf8Bom = $bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and
        $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF
    $offset = if ($hasUtf8Bom) { 3 } else { 0 }
    $content = [System.Text.Encoding]::UTF8.GetString($bytes, $offset, $bytes.Length - $offset)
    $newLine = if ($content.Contains("`r`n")) { "`r`n" } else { "`n" }
    $lines = [System.Text.RegularExpressions.Regex]::Split($content, '\r\n|\n')
    $changed = $false

    for ($index = 0; $index -lt $lines.Length; $index++) {
        $updatedLine = & $Transform $lines[$index]
        if ($updatedLine -ne $lines[$index]) {
            $lines[$index] = $updatedLine
            $changed = $true
        }
    }
    if (-not $changed) { return $false }

    $updated = [string]::Join($newLine, $lines)
    $encoding = New-Object System.Text.UTF8Encoding($hasUtf8Bom)
    [System.IO.File]::WriteAllText($Path, $updated, $encoding)
    return $true
}

function Update-BridgeNetworkDefaults {
    param([string]$Path)
    $changed = Update-PropertiesLines -Path $Path -Transform {
        param([string]$Line)
        if ($Line -eq 'bridge.control.bind=127.0.0.1') {
            return "bridge.control.bind=$ControlBind"
        }
        if ($Line -eq 'bridge.control.port=8090') {
            return "bridge.control.port=$ControlPort"
        }
        if ($Line -match '^outputs\.(\d+)\.endpoint=ws://127\.0\.0\.1:8081/bridge/v1/channels/local$') {
            return "outputs.$($Matches[1]).endpoint=ws://127.0.0.1:$LiveWsPort/bridge/v1/channels/local"
        }
        if ($Line -match '^outputs\.(\d+)\.endpoint=ws\\://127\.0\.0\.1\\:8081/bridge/v1/channels/local$') {
            return "outputs.$($Matches[1]).endpoint=ws\://127.0.0.1\:$LiveWsPort/bridge/v1/channels/local"
        }
        return $Line
    }
    if ($changed) {
        Write-Note "Frühere Installer-Netzwerkdefaults auf den festen Portblock migriert: $bridgeConfig"
    }
}

function Update-LiveNetworkDefaults {
    param([string]$Path)
    $changed = Update-PropertiesLines -Path $Path -Transform {
        param([string]$Line)
        if ($Line -eq 'winlaufen.live.http.port=8080') {
            return "winlaufen.live.http.port=$LiveHttpPort"
        }
        if ($Line -eq 'winlaufen.live.websocket.port=8081') {
            return "winlaufen.live.websocket.port=$LiveWsPort"
        }
        return $Line
    }
    if ($changed) {
        Write-Note "Frühere Installer-Netzwerkdefaults auf den festen Portblock migriert: $liveConfig"
    }
}

if ($installBridge) {
    $stagedBridgeConfig = Get-StagedPath $bridgeConfig
    if (Test-Path -LiteralPath $stagedBridgeConfig) {
        Update-BridgeNetworkDefaults -Path $stagedBridgeConfig
        Write-Note "Bestehende Bridge-Konfiguration beibehalten: $bridgeConfig"
    } else {
        if ($Profile -eq 'AllInOne') {
            # All-in-One: lokaler Live Server als reguläres Output Target.
            $content = @"
# $ProductName - Bridge (Profil: All-in-One)
# Erzeugt bei der Erstinstallation. Änderungen bitte über Bridge Control
# vornehmen: http://<bridge-ip>:$ControlPort/
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
# anschließend über Bridge Control pflegen: http://<bridge-ip>:$ControlPort/
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
        Update-LiveNetworkDefaults -Path $stagedLiveConfig
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
"$javaExe" "-D$BridgeConfigProperty=$bridgeConfig" -jar "$InstallPrefix\lib\$BridgeJar"
exit /b %errorlevel%
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
& '$javaExe' @arguments
exit `$LASTEXITCODE
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

function Test-TaskRunning {
    param([string]$TaskName)
    $task = Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
    return ($task -and $task.State -eq 'Running')
}

function Test-HttpEndpoint {
    param([int]$Port)
    try {
        $response = Invoke-WebRequest -Uri "http://127.0.0.1:$Port/" -UseBasicParsing `
            -TimeoutSec 2 -ErrorAction Stop
        return ($response.StatusCode -eq 200)
    } catch {
        return $false
    }
}

function Wait-InstalledRuntime {
    param([string]$TaskName, [int[]]$Ports, [int]$HttpPort)

    $ready = $false
    foreach ($attempt in 1..20) {
        $listenersReady = $true
        foreach ($port in $Ports) {
            if (-not (Get-ListenerOwner -Port $port)) { $listenersReady = $false }
        }
        if ((Test-TaskRunning -TaskName $TaskName) -and $listenersReady -and
                (Test-HttpEndpoint -Port $HttpPort)) {
            $ready = $true
            break
        }
        Start-Sleep -Seconds 1
    }
    if (-not $ready) {
        $state = (Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue).State
        throw "Hintergrunddienst '$TaskName' wurde nicht betriebsbereit (Status: $state).`n`nDie Installation wurde nicht erfolgreich abgeschlossen."
    }

    Start-Sleep -Seconds 3
    $stable = Test-TaskRunning -TaskName $TaskName
    foreach ($port in $Ports) {
        if (-not (Get-ListenerOwner -Port $port)) { $stable = $false }
    }
    if (-not (Test-HttpEndpoint -Port $HttpPort)) { $stable = $false }
    if (-not $stable) {
        throw "Hintergrunddienst '$TaskName' blieb während der Startphase nicht betriebsbereit.`n`nDie Installation wurde nicht erfolgreich abgeschlossen."
    }
}

function Get-LocalOutputState {
    param($Status)
    if (-not $Status) { return 'UNBEKANNT' }
    $local = $Status.outputs | Where-Object { $_.targetId -eq 'local' } |
        Select-Object -First 1
    if (-not $local) { return 'UNBEKANNT' }
    return [string]$local.state
}

function Get-BridgeOperationalDiagnostic {
    try {
        $config = Invoke-RestMethod -Uri "http://127.0.0.1:$ControlPort/api/v1/config" `
            -TimeoutSec 2 -ErrorAction Stop
        $status = $null
        $attempts = if ($Profile -eq 'AllInOne') { 3 } else { 1 }
        foreach ($attempt in 1..$attempts) {
            $status = Invoke-RestMethod -Uri "http://127.0.0.1:$ControlPort/api/v1/status" `
                -TimeoutSec 2 -ErrorAction Stop
            if ($Profile -ne 'AllInOne' -or
                    (Get-LocalOutputState -Status $status) -eq 'CONNECTED') {
                break
            }
            if ($attempt -lt $attempts) { Start-Sleep -Seconds 1 }
        }
        return [pscustomobject]@{
            Available = $true
            Config = $config
            Status = $status
            Error = $null
        }
    } catch {
        return [pscustomobject]@{
            Available = $false
            Config = $null
            Status = $null
            Error = $_.Exception.Message
        }
    }
}

if ([string]::IsNullOrWhiteSpace($StagingRoot) -and -not $SkipTasks) {
    if ($installLive) {
        Register-BackgroundTask -TaskName $LiveTaskName -Launcher $liveLauncher
    }
    if ($installBridge) {
        Register-BackgroundTask -TaskName $BridgeTaskName -Launcher $bridgeLauncher
    }

    if ($installLive) {
        Wait-InstalledRuntime -TaskName $LiveTaskName `
            -Ports @($LiveHttpPort, $LiveWsPort) -HttpPort $LiveHttpPort
    }
    if ($installBridge) {
        Wait-InstalledRuntime -TaskName $BridgeTaskName `
            -Ports @($ControlPort) -HttpPort $ControlPort
    }
    $localRuntimeValidated = $true

    # Windows verwaltet ausschließlich die eigenen eingehenden Regeln. Die
    # Synchronisierung erfolgt erst nach erfolgreicher Runtime-Validierung.
    Sync-WindowsFirewallRules

    # Beim Profilwechsel bleibt die alte Aufgabe bis zum erfolgreichen Start
    # der gewünschten Runtime als einfache Rückfallmöglichkeit registriert.
    if (-not $installLive) {
        Remove-BackgroundTask -TaskName $LiveTaskName
    }

    # Quellen und Output Targets werden erst nach erfolgreicher lokaler
    # Installation diagnostiziert. Ihr Verbindungszustand ist niemals ein
    # Installationsfehler.
    if ($installBridge) {
        $bridgeDiagnostic = Get-BridgeOperationalDiagnostic
    }
}

} catch {
    $installationFailure = $_
    if ($installationWasStopped -and $previouslyRunningTasks.Count -gt 0) {
        Write-Warning "Installation fehlgeschlagen; zuvor laufende WinLaufen-Web-Aufgaben werden erneut gestartet."
        Restore-PreviouslyRunningTasks -TaskNames $previouslyRunningTasks
    }
    throw $installationFailure
}

# ------------------------------------------------------------ Abschlussmeldung

function Write-BridgeOperationalDiagnostic {
    param($Diagnostic)

    if (-not $Diagnostic -or -not $Diagnostic.Available) {
        Write-Host "  WARNUNG: Der aktuelle Bridge-Verbindungsstatus konnte nicht gelesen werden."
        Write-Host "  Die lokale Installation ist erfolgreich; Betriebsstatus später in Bridge Control prüfen."
        return
    }

    $sourceState = [string]$Diagnostic.Status.sourceHealth
    $sourceHost = [string]$Diagnostic.Config.sourceHost
    $sourcePort = [int]$Diagnostic.Config.sourcePort
    Write-Host "WinLaufen-Quelle:"
    if ($sourceState -eq 'CONNECTED') {
        Write-Host "  OK: CONNECTED"
    } else {
        Write-Host "  WARNUNG: $sourceState"
    }
    Write-Host "  Ziel: ${sourceHost}:$sourcePort"
    if ($sourceState -ne 'CONNECTED') {
        Write-Host @"

  Die Bridge wurde erfolgreich installiert. WinLaufen ist derzeit nicht verbunden.
  Nächste Schritte:
  - WinLaufen bzw. die Sprecher-PC-Schnittstelle starten.
  - Host/IP in Bridge Control prüfen.
  - TCP $SourcePort zwischen Bridge und WinLaufen-PC prüfen.
  - Falls WinLaufen auf einem anderen Rechner läuft, dessen Host/IP in Bridge Control eintragen.
"@
    }

    Write-Host ""
    Write-Host "Output Targets:"
    $targets = @($Diagnostic.Config.targets)
    if ($targets.Count -eq 0) {
        Write-Host "  HINWEIS: keine Output Targets konfiguriert."
        Write-Host "  Targets können später in Bridge Control eingetragen werden."
        return
    }

    foreach ($target in $targets) {
        $runtime = $Diagnostic.Status.outputs | Where-Object {
            $_.targetId -eq $target.id
        } | Select-Object -First 1
        $state = if ($runtime) { [string]$runtime.state } else { 'UNBEKANNT' }

        Write-Host ""
        Write-Host "  Output Target:"
        Write-Host "    ID: $($target.id)"
        Write-Host "    Typ: $($target.type)"
        Write-Host "    Endpoint: $($target.endpoint)"
        try {
            $targetUri = [Uri][string]$target.endpoint
            Write-Host "    Ziel: $($targetUri.Host):$($targetUri.Port)"
        } catch {
            # Die gespeicherte URL bleibt sichtbar; ein Diagnosefehler blockiert
            # die bereits erfolgreiche lokale Installation nicht.
        }
        if (-not [bool]$target.enabled) {
            Write-Host "    Status: deaktiviert"
        } elseif ($state -eq 'CONNECTED') {
            Write-Host "    OK: CONNECTED"
        } else {
            Write-Host "    WARNUNG: $state"
            if ($Profile -eq 'AllInOne' -and $target.id -eq 'local') {
                Write-Host @"

    WARNUNG: Der lokale Datenpfad Bridge -> Live Server ist noch nicht verbunden.
    Bridge und Live Server wurden erfolgreich installiert.
    Prüfen Sie Bridge Control und die lokale Target-Konfiguration.
"@
            } else {
                Write-Host @"

    Das Target ist derzeit nicht erreichbar. Die Bridge wurde trotzdem erfolgreich installiert.
    Prüfen:
    - Presentation Node installiert und gestartet?
    - Host/IP bzw. URL korrekt?
    - TCP $LiveWsPort erreichbar?
    - Channel/Secret korrekt?
    - Firewall/VLAN/Router?
"@
            }
        }
    }
}

function Write-InstallationReport {
    Write-Host ""
    Write-Host "============================================================"
    Write-Host "$ProductName - Installation erfolgreich"
    Write-Host "============================================================"
    Write-Host ""
    Write-Host "Lokale Komponenten:"
    if ($localRuntimeValidated) {
        if ($installBridge) {
            Write-Host "  OK: Bridge Service             läuft"
            Write-Host "  OK: Bridge Control             TCP $ControlPort erreichbar"
        }
        if ($installLive) {
            Write-Host "  OK: Live Server                läuft"
            Write-Host "  OK: Web View                   TCP $LiveHttpPort erreichbar"
            Write-Host "  OK: Live WebSocket             TCP $LiveWsPort lauscht"
        }
    } else {
        if ($installBridge) { Write-Host "  HINWEIS: Bridge installiert; Startprüfung wurde übersprungen." }
        if ($installLive) { Write-Host "  HINWEIS: Live Server installiert; Startprüfung wurde übersprungen." }
    }

    Write-Host ""
    Write-Host "Verbindungen / Betriebsbereitschaft:"
    if ($installBridge -and $localRuntimeValidated) {
        Write-BridgeOperationalDiagnostic -Diagnostic $bridgeDiagnostic
    } elseif ($installBridge) {
        Write-Host "  HINWEIS: nach dem Dienststart in Bridge Control prüfen."
    }
}

Write-InstallationReport

Write-Host ""
Write-Host "Windows Defender Firewall: erforderliche eingehende TCP-Regeln wurden"
Write-Host "für die Netzwerkprofile Private und Domain eingerichtet. Es wurde keine"
Write-Host "Freigabe für das Profil Public angelegt."

if ($installLive) {
    Write-Host @"

Hinweis zur Sicherheit: Diese Version ist ein Prototyp für kontrollierte Netze
und verwendet ein bekanntes Ingest-Secret. Port $LiveWsPort darf nicht aus nicht
vertrauenswürdigen Netzen erreichbar sein. Details in README.md, Abschnitt
"Known prototype security limitation".
"@
}
