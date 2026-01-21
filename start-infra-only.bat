@echo off
chcp 65001 > nul
title Orai Infrastructure

echo ============================================
echo   Starting Infrastructure Only
echo ============================================
echo.

docker-compose up -d mysql mongodb redis

echo.
echo   MySQL:    localhost:3306 (user: orai / pw: orai)
echo   MongoDB:  localhost:27017 (user: orai / pw: orai)
echo   Redis:    localhost:6379
echo.
echo ============================================
pause
