<#
.SYNOPSIS
    Starts the local dev stack: Postgres + MinIO (always), Redis (opt-in),
    the backend (mvn spring-boot:run) and the frontend (npm run dev).

.PARAMETER WithRedis
    Also starts the `redis` Docker Compose service (profile "redis") and runs
    the backend with CACHE_TYPE=redis instead of the default in-process
    caffeine cache (D-056). Omit it for a normal single-instance dev run.

.EXAMPLE
    scripts\start-dev.ps1
    scripts\start-dev.ps1 -WithRedis
#>
param(
    [switch]$WithRedis,
    [string]$MvnCommand = "mvn",
    [string]$NpmCommand = "npm"
)

$ErrorActionPreference = "Stop"

$repoRoot    = Split-Path -Parent $PSScriptRoot
$composeFile = Join-Path $repoRoot "infrastructure\docker-compose.yml"
$stateFile   = Join-Path $repoRoot ".dev-stack.json"
$backendDir  = Join-Path $repoRoot "backend"
$frontendDir = Join-Path $repoRoot "frontend"
$backendLog  = Join-Path $backendDir "target\dev-backend.log"
$frontendLog = Join-Path $frontendDir "dev-frontend.log"

if (Test-Path $stateFile) {
    $existing = Get-Content $stateFile -Raw | ConvertFrom-Json
    $stillRunning = @($existing.backendPid, $existing.frontendPid) | Where-Object {
        $_ -and (Get-Process -Id $_ -ErrorAction SilentlyContinue)
    }
    if ($stillRunning.Count -gt 0) {
        Write-Warning "$stateFile already exists and at least one tracked process is still running (PIDs: $($stillRunning -join ', '))."
        Write-Warning "Run scripts\stop-dev.ps1 first."
        exit 1
    }
    Write-Host "Found a stale $stateFile with no live processes - removing it."
    Remove-Item $stateFile
}

Write-Host "==> Postgres + MinIO$(if ($WithRedis) { ' + Redis' })"
if ($WithRedis) {
    & docker compose -f $composeFile --profile redis up -d --wait
} else {
    & docker compose -f $composeFile up -d --wait
}
if ($LASTEXITCODE -ne 0) { throw "docker compose up failed (exit $LASTEXITCODE)" }

$env:CACHE_TYPE = if ($WithRedis) { "redis" } else { "caffeine" }

New-Item -ItemType Directory -Force -Path (Split-Path $backendLog) | Out-Null

Write-Host "==> Backend (mvn spring-boot:run, CACHE_TYPE=$($env:CACHE_TYPE)) -> $backendLog"
$backend = Start-Process -FilePath $MvnCommand `
    -ArgumentList @("spring-boot:run", "-Dspring-boot.run.profiles=local") `
    -WorkingDirectory $backendDir `
    -RedirectStandardOutput $backendLog `
    -RedirectStandardError "$backendLog.err" `
    -WindowStyle Hidden -PassThru

Write-Host "==> Frontend (npm run dev) -> $frontendLog"
$frontend = Start-Process -FilePath $NpmCommand `
    -ArgumentList @("run", "dev") `
    -WorkingDirectory $frontendDir `
    -RedirectStandardOutput $frontendLog `
    -RedirectStandardError "$frontendLog.err" `
    -WindowStyle Hidden -PassThru

[PSCustomObject]@{
    startedAt   = (Get-Date).ToString("o")
    withRedis   = [bool]$WithRedis
    backendPid  = $backend.Id
    frontendPid = $frontend.Id
} | ConvertTo-Json | Set-Content -Path $stateFile

Write-Host ""
Write-Host "Waiting for the backend to answer on :8090 ..."
$deadline = (Get-Date).AddSeconds(90)
$up = $false
while ((Get-Date) -lt $deadline) {
    try {
        $resp = Invoke-WebRequest -Uri "http://localhost:8090/actuator/health" -UseBasicParsing -TimeoutSec 3
        if ($resp.StatusCode -eq 200) { $up = $true; break }
    } catch {
        Start-Sleep -Seconds 2
    }
}

if ($up) {
    Write-Host "Backend is up:  http://localhost:8090 (PID $($backend.Id))"
} else {
    Write-Warning "Backend did not answer within 90s - check $backendLog"
}
Write-Host "Frontend:       http://localhost:5173 (PID $($frontend.Id); Vite takes a few seconds - check $frontendLog)"
if ($WithRedis) { Write-Host "Redis:          localhost:6379 (docker compose --profile redis)" }
Write-Host ""
Write-Host "Stop everything with: scripts\stop-dev.ps1"
