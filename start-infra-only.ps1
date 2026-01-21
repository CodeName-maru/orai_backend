# Orai Backend - Start Infrastructure Only
# Usage: .\start-infra-only.ps1

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  Starting Infrastructure Only" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

docker-compose up -d mysql mongodb redis

Write-Host ""
Write-Host "  MySQL:   " -NoNewline; Write-Host "localhost:3306" -ForegroundColor Cyan -NoNewline; Write-Host " (user: orai / pw: orai)"
Write-Host "  MongoDB: " -NoNewline; Write-Host "localhost:27017" -ForegroundColor Cyan -NoNewline; Write-Host " (user: orai / pw: orai)"
Write-Host "  Redis:   " -NoNewline; Write-Host "localhost:6379" -ForegroundColor Cyan
Write-Host ""
Write-Host "============================================" -ForegroundColor Green
Write-Host ""
Read-Host "Press Enter to close"
