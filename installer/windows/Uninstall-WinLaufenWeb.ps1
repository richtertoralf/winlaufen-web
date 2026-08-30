<#
.SYNOPSIS
    Deinstalliert WinLaufen Web unter Windows 11.

.DESCRIPTION
    Stoppt und entfernt die geplanten Aufgaben und löscht die Programmdateien.
    Ohne -Purge bleibt C:\ProgramData\WinLaufen Web erhalten, damit eine
    gepflegte WinLaufen-Adresse und Target-Liste eine Neuinstallation überlebt.

.EXAMPLE
    .\Uninstall-WinLaufenWeb.ps1
    .\Uninstall-WinLaufenWeb.ps1 -Purge
#>
[CmdletBinding()]
param(
    [switch]$Purge,
    [string]$StagingRoot
)

$ErrorActionPreference = 'Stop'

$InstallPrefix  = Join-Path $env:ProgramFiles 'WinLaufen Web'
$ConfigDir      = Join-Path $env:ProgramData 'WinLaufen Web'
$BridgeTaskName = 'WinLaufen Web Bridge'
$LiveTaskName   = 'WinLaufen Web Live Server'
$FirewallRuleNames = @(
    'WinLaufenWeb-HTTP-44440',
    'WinLaufenWeb-WebSocket-44441',
    'WinLaufenWeb-BridgeControl-44442'
)

function Get-StagedPath {
    param([string]$Path)
    if ([string]::IsNullOrWhiteSpace($StagingRoot)) { return $Path }
    $qualifier = [System.IO.Path]::GetPathRoot($Path)
    return (Join-Path $StagingRoot $Path.Substring($qualifier.Length))
}

if ([string]::IsNullOrWhiteSpace($StagingRoot)) {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object Security.Principal.WindowsPrincipal($identity)
    if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
        throw "Bitte PowerShell als Administrator ausführen."
    }

    Write-Host "== Dienste stoppen und entfernen =="
    foreach ($task in @($BridgeTaskName, $LiveTaskName)) {
        if (Get-ScheduledTask -TaskName $task -ErrorAction SilentlyContinue) {
            Stop-ScheduledTask -TaskName $task -ErrorAction SilentlyContinue
            Unregister-ScheduledTask -TaskName $task -Confirm:$false
            Write-Host "  entfernt: $task"
        }
    }

    Write-Host "== Eigene Windows-Firewallregeln entfernen =="
    foreach ($ruleName in $FirewallRuleNames) {
        $rule = Get-NetFirewallRule -Name $ruleName -ErrorAction SilentlyContinue
        if ($rule) {
            $rule | Remove-NetFirewallRule
            Write-Host "  entfernt: $ruleName"
        }
    }

    # Noch laufende Prozesse dieser Installation beenden.
    Get-CimInstance Win32_Process -Filter "Name like '%java%'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -and $_.CommandLine -like "*$InstallPrefix*" } |
        ForEach-Object {
            Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
            Write-Host "  beendet: PID $($_.ProcessId)"
        }
}

Write-Host "== Programmdateien entfernen =="
$stagedPrefix = Get-StagedPath $InstallPrefix
if (Test-Path -LiteralPath $stagedPrefix) {
    Remove-Item -LiteralPath $stagedPrefix -Recurse -Force
    Write-Host "  entfernt: $InstallPrefix"
}

if ($Purge) {
    Write-Host "== Konfiguration und Zustand entfernen (-Purge) =="
    $stagedConfig = Get-StagedPath $ConfigDir
    if (Test-Path -LiteralPath $stagedConfig) {
        Remove-Item -LiteralPath $stagedConfig -Recurse -Force
        Write-Host "  entfernt: $ConfigDir"
    }
} else {
    Write-Host ""
    Write-Host "Konfiguration und Zustand wurden bewusst nicht entfernt:"
    Write-Host "  $ConfigDir"
    Write-Host "Zum vollständigen Entfernen: .\Uninstall-WinLaufenWeb.ps1 -Purge"
}

Write-Host ""
Write-Host "Deinstallation abgeschlossen."
