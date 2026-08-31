@echo off
setlocal
rem ============================================================
rem  Linux AIOps Agent - stop the one-click-started backend
rem ============================================================
set PORT=18080
set FOUND=0

for /f "tokens=5" %%p in ('netstat -ano ^| findstr ":%PORT% " ^| findstr /i "LISTENING"') do (
    echo [i] killing PID %%p
    taskkill /PID %%p /F >nul 2>nul
    set FOUND=1
)

if %FOUND%==0 (
    echo [i] no backend running on port %PORT%
) else (
    echo [ok] backend stopped.
)
pause
