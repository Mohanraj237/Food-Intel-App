<#
.SYNOPSIS
    Stops the local dev stack started by start-dev.ps1: backend, frontend,
    and the Docker services (Postgres, MinIO, Redis if it was running).

.PARAMETER RemoveVolumes
    Also drops the Postgres/MinIO Docker volumes (docker compose down -v).

.EXAMPLE
    scripts\stop-dev.ps1
    scripts\stop-dev.ps1 -RemoveVolumes
#>
param(
    [switch]$RemoveVolumes
)

$ErrorActionPreference = "Stop"

$repoRoot    = Split-Path -Parent $PSScriptRoot
$composeFile = Join-Path $repoRoot "infrastructure\docker-compose.yml"
$stateFile   = Join-Path $repoRoot ".dev-stack.json"

function Stop-ProcessTree {
    param([int]$ProcessId)
    $children = Get-CimInstance Win32_Process -Filter "ParentProcessId=$ProcessId" -ErrorAction SilentlyContinue
    foreach ($child in $children) {
        Stop-ProcessTree -ProcessId $child.ProcessId
    }
    Stop-Process -Id $ProcessId -Force -ErrorAction SilentlyContinue
}

if (Test-Path $stateFile) {
    $state = Get-Content $stateFile -Raw | ConvertFrom-Json
    foreach ($entry in @(
        @{ Name = "backend";  Id = $state.backendPid },
        @{ Name = "frontend"; Id = $state.frontendPid }
    )) {
        if ($entry.Id -and (Get-Process -Id $entry.Id -ErrorAction SilentlyContinue)) {
            Write-Host "==> Stopping $($entry.Name) (PID $($entry.Id) and its child processes)"
            Stop-ProcessTree -ProcessId $entry.Id
        } else {
            Write-Host "==> $($entry.Name) is not running"
        }
    }
    Remove-Item $stateFile
} else {
    Write-Warning "No $stateFile found - backend/frontend were not started by start-dev.ps1, or are already stopped. Skipping process cleanup."
}

# --profile redis is included unconditionally: it only affects which services `down`
# considers, and removing a container that was never started is a no-op.
Write-Host "==> Docker services (Postgres, MinIO, Redis)"
$downArgs = @("compose", "-f", $composeFile, "--profile", "redis", "down")
if ($RemoveVolumes) { $downArgs += "-v" }
& docker @downArgs
if ($LASTEXITCODE -ne 0) { throw "docker compose down failed (exit $LASTEXITCODE)" }

Write-Host ""
Write-Host "Stack stopped.$(if ($RemoveVolumes) { ' Postgres/MinIO volumes removed.' })"
