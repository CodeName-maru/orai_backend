@echo off
chcp 65001 > nul
title Orai Development Stopper

echo ============================================
echo   Stopping Orai Development Environment
echo ============================================
echo.

:: Java 프로세스 종료 (Spring Boot 서비스들)
echo Stopping Spring Boot services...
taskkill /f /fi "WINDOWTITLE eq Discovery Service*" 2>nul
taskkill /f /fi "WINDOWTITLE eq Config Service*" 2>nul
taskkill /f /fi "WINDOWTITLE eq Gateway Service*" 2>nul
taskkill /f /fi "WINDOWTITLE eq User Service*" 2>nul
taskkill /f /fi "WINDOWTITLE eq Calendar Service*" 2>nul
taskkill /f /fi "WINDOWTITLE eq Chat Service*" 2>nul
taskkill /f /fi "WINDOWTITLE eq Etc Service*" 2>nul

:: Docker 컨테이너 중지
echo.
echo Stopping Docker containers...
docker-compose stop mysql mongodb redis

echo.
echo ============================================
echo   All services stopped!
echo ============================================
pause
