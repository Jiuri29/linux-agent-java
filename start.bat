@echo off
setlocal
rem ============================================================
rem  Linux AIOps Agent - one-click start (Windows)
rem  Double-click this file. It will:
rem    1) reuse the running backend if port 18080 is in use
rem    2) build frontend/backend only when artifacts are missing
rem    3) start the backend with data stored in .runtime
rem    4) wait for health check, then open the console in browser
rem  Console URL: http://127.0.0.1:18080/
rem ============================================================
cd /d "%~dp0"

set PORT=18080
set URL=http://127.0.0.1:%PORT%/
set JARFILE=

rem ---------- 1. already running? just open the browser ----------
netstat -ano | findstr ":%PORT% " | findstr /i "LISTENING" >nul
if %errorlevel%==0 (
    echo [i] backend already running on port %PORT%
    echo [i] console: %URL%
    start "" %URL%
    exit /b 0
)

rem ---------- 2. java check ----------
where java >nul 2>nul
if not %errorlevel%==0 (
    echo [x] java not found in PATH. Please install JDK 17+ first.
    pause
    exit /b 1
)

rem ---------- 3. frontend build (only if missing) ----------
if not exist "frontend\build\index.html" (
    where npm >nul 2>nul
    if errorlevel 1 (
        echo [!] frontend\build missing and npm not found. Console UI will be unavailable, API still works.
    ) else (
        echo [i] building frontend, please wait ...
        pushd frontend
        call npm install --no-audit --no-fund
        call npm run build
        popd
    )
)

rem ---------- 4. backend jar (build only if missing) ----------
for %%f in ("target\linux-aiops-agent-*.jar") do set JARFILE=%%f
if "%JARFILE%"=="" (
    echo [i] backend jar not found, building with maven ...
    set MVNCMD=
    where mvn.cmd >nul 2>nul && set MVNCMD=mvn.cmd
    if not defined MVNCMD where mvn >nul 2>nul && set MVNCMD=mvn
    if not defined MVNCMD if exist "%MAVEN_HOME%\bin\mvn.cmd" set "MVNCMD=%MAVEN_HOME%\bin\mvn.cmd"
    if not defined MVNCMD if exist "D:\IntelliJ IDEA 2026.2.1\plugins\maven-plugin\lib\maven3\bin\mvn.cmd" set "MVNCMD=D:\IntelliJ IDEA 2026.2.1\plugins\maven-plugin\lib\maven3\bin\mvn.cmd"
    if not defined MVNCMD (
        for /d %%d in ("C:\Program Files\JetBrains\*") do if exist "%%d\plugins\maven-plugin\lib\maven3\bin\mvn.cmd" set "MVNCMD=%%d\plugins\maven-plugin\lib\maven3\bin\mvn.cmd"
    )
    if not defined MVNCMD (
        echo [x] maven not found. Build the jar once with IDE, or install maven and retry.
        pause
        exit /b 1
    )
    echo [i] using maven: %MVNCMD%
    call "%MVNCMD%" -q -DskipTests package
    if errorlevel 1 (
        echo [x] maven build failed.
        pause
        exit /b 1
    )
    for %%f in ("target\linux-aiops-agent-*.jar") do set JARFILE=%%f
)
echo [i] using jar: %JARFILE%

rem ---------- 5. start backend (minimized window, logs to file) ----------
if not exist ".runtime" mkdir ".runtime"
start "linux-aiops-agent" /min cmd /c "java -jar %JARFILE% > .runtime\console.log 2>&1"

rem ---------- 6. wait for health check ----------
echo [i] waiting for backend to come up ...
powershell -NoProfile -Command "foreach ($i in 1..60) { try { Invoke-RestMethod 'http://127.0.0.1:%PORT%/api/health' -TimeoutSec 2 | Out-Null; exit 0 } catch { Start-Sleep -Milliseconds 500 } }; exit 1"
if errorlevel 1 (
    echo [x] backend failed to start. Check .runtime\console.log
    pause
    exit /b 1
)

echo.
echo ============================================================
echo   Linux AIOps console is ready:
echo     %URL%
echo   Data dir : %CD%\.runtime
echo   Logs     : %CD%\.runtime\console.log
echo   Stop     : run stop.bat
echo ============================================================
start "" %URL%
timeout /t 5 >nul
