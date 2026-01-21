# Orai Backend - Stop Development Environment
# Usage: .\stop-dev.ps1

$Host.UI.RawUI.WindowTitle = "Orai Dev Stopper"

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  Stopping Orai Development Environment" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

# Gradle 데몬과 Java 프로세스 종료
Write-Host "Stopping Spring Boot services..." -ForegroundColor Yellow

# gradlew bootRun으로 실행된 Java 프로세스 찾아서 종료
Get-Process -Name "java" -ErrorAction SilentlyContinue | ForEach-Object {
    $cmdLine = (Get-CimInstance Win32_Process -Filter "ProcessId = $($_.Id)").CommandLine
    if ($cmdLine -match "spring-boot|bootRun") {
        Write-Host "  Stopping Java process: $($_.Id)" -ForegroundColor Gray
        Stop-Process -Id $_.Id -Force -ErrorAction SilentlyContinue
    }
}

# Gradle 데몬 종료
Write-Host "Stopping Gradle daemons..." -ForegroundColor Yellow
$projectRoot = $PSScriptRoot
Push-Location $projectRoot
& .\gradlew --stop 2>$null
Pop-Location

# Docker 컨테이너 중지
Write-Host ""
Write-Host "Stopping Docker containers..." -ForegroundColor Yellow
docker-compose stop mysql mongodb redis

Write-Host ""
Write-Host "============================================" -ForegroundColor Green
Write-Host "  All services stopped!" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Green
Write-Host ""
Read-Host "Press Enter to close"
