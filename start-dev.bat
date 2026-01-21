@echo off
chcp 65001 > nul
title Orai Development Launcher

echo ============================================
echo   Orai Backend - Development Environment
echo ============================================
echo.

:: 1. 인프라 실행 (Docker)
echo [1/3] Starting infrastructure (MySQL, MongoDB, Redis)...
docker-compose up -d mysql mongodb redis

if %errorlevel% neq 0 (
    echo ERROR: Failed to start Docker containers
    echo Make sure Docker Desktop is running!
    pause
    exit /b 1
)

:: 인프라 준비 대기
echo Waiting for infrastructure to be ready...
timeout /t 10 /nobreak > nul

:: 2. Spring Cloud 인프라 서비스 실행
echo.
echo [2/3] Starting Spring Cloud infrastructure...

echo Starting Discovery Service (Eureka)...
start "Discovery Service" cmd /k "cd /d %~dp0discovery-service && gradlew bootRun"
timeout /t 30 /nobreak > nul

echo Starting Config Service...
start "Config Service" cmd /k "cd /d %~dp0config-service && gradlew bootRun"
timeout /t 20 /nobreak > nul

echo Starting Gateway Service...
start "Gateway Service" cmd /k "cd /d %~dp0gateway-service && gradlew bootRun"
timeout /t 15 /nobreak > nul

:: 3. 비즈니스 서비스 실행
echo.
echo [3/3] Starting business services...

start "User Service" cmd /k "cd /d %~dp0user-service && gradlew bootRun"
start "Calendar Service" cmd /k "cd /d %~dp0calendar-service && gradlew bootRun"
start "Chat Service" cmd /k "cd /d %~dp0chat-service && gradlew bootRun"
start "Etc Service" cmd /k "cd /d %~dp0etc-service && gradlew bootRun"

echo.
echo ============================================
echo   All services are starting!
echo ============================================
echo.
echo   Eureka Dashboard: http://localhost:8761
echo   API Gateway:      http://localhost:8080
echo.
echo   To stop all services, run: stop-dev.bat
echo ============================================
pause
