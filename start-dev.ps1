# Orai Backend - Development Environment Launcher
# Usage: .\start-dev.ps1 or right-click > Run with PowerShell

$ErrorActionPreference = "Stop"
$Host.UI.RawUI.WindowTitle = "Orai Dev Launcher"

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  Orai Backend - Development Environment" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

$projectRoot = $PSScriptRoot

# 1. 인프라 실행
Write-Host "[1/3] Starting infrastructure (MySQL, MongoDB, Redis)..." -ForegroundColor Yellow
docker-compose up -d mysql mongodb redis

if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: Failed to start Docker containers" -ForegroundColor Red
    Write-Host "Make sure Docker Desktop is running!" -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit 1
}

Write-Host "Waiting for infrastructure to be ready..." -ForegroundColor Gray
Start-Sleep -Seconds 10

# 2. Spring Cloud 인프라 서비스
Write-Host ""
Write-Host "[2/3] Starting Spring Cloud infrastructure..." -ForegroundColor Yellow

Write-Host "  Starting Discovery Service (Eureka)..." -ForegroundColor White
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$projectRoot\discovery-service'; .\gradlew bootRun" -WindowStyle Normal
Start-Sleep -Seconds 30

Write-Host "  Starting Config Service..." -ForegroundColor White
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$projectRoot\config-service'; .\gradlew bootRun" -WindowStyle Normal
Start-Sleep -Seconds 20

Write-Host "  Starting Gateway Service..." -ForegroundColor White
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$projectRoot\gateway-service'; .\gradlew bootRun" -WindowStyle Normal
Start-Sleep -Seconds 15

# 3. 비즈니스 서비스
Write-Host ""
Write-Host "[3/3] Starting business services..." -ForegroundColor Yellow

Write-Host "  Starting User Service..." -ForegroundColor White
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$projectRoot\user-service'; .\gradlew bootRun" -WindowStyle Normal

Write-Host "  Starting Calendar Service..." -ForegroundColor White
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$projectRoot\calendar-service'; .\gradlew bootRun" -WindowStyle Normal

Write-Host "  Starting Chat Service..." -ForegroundColor White
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$projectRoot\chat-service'; .\gradlew bootRun" -WindowStyle Normal

Write-Host "  Starting Etc Service..." -ForegroundColor White
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$projectRoot\etc-service'; .\gradlew bootRun" -WindowStyle Normal

Write-Host ""
Write-Host "============================================" -ForegroundColor Green
Write-Host "  All services are starting!" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Green
Write-Host ""
Write-Host "  Eureka Dashboard: " -NoNewline; Write-Host "http://localhost:8761" -ForegroundColor Cyan
Write-Host "  API Gateway:      " -NoNewline; Write-Host "http://localhost:8080" -ForegroundColor Cyan
Write-Host ""
Write-Host "  To stop: " -NoNewline; Write-Host ".\stop-dev.ps1" -ForegroundColor Yellow
Write-Host "============================================" -ForegroundColor Green
Write-Host ""
Read-Host "Press Enter to close this window"
