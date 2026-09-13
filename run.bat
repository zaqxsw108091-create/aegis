@echo off
setlocal
cd /d "%~dp0"

echo ============================================================
echo   Aegis security system
echo   - Admin login:  daeyoung0 / dae0nooli
echo   - Dashboard:    http://localhost:8080/admin/dashboard
echo   - To stop: close this window, press Ctrl+C, or run stop.bat
echo ============================================================
echo.

rem ---------------------------------------------------------------
rem 1) Locate Java 21 (portable JDK -> JAVA_HOME -> PATH)
rem    Explicit paths are used everywhere: cmd may be configured with
rem    NoDefaultCurrentDirectoryInExePath, which breaks bare names.
rem ---------------------------------------------------------------
set "JAVA_EXE="
if exist "C:\Users\USER\.jdks\jdk-21.0.11+10\bin\java.exe" (
    set "JAVA_HOME=C:\Users\USER\.jdks\jdk-21.0.11+10"
    set "JAVA_EXE=C:\Users\USER\.jdks\jdk-21.0.11+10\bin\java.exe"
)
if not defined JAVA_EXE if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
if not defined JAVA_EXE for %%J in (java.exe) do if not "%%~$PATH:J"=="" set "JAVA_EXE=%%~$PATH:J"

if not defined JAVA_EXE (
    echo [ERROR] Java not found. Install Java 21: https://adoptium.net
    echo         Then run this file again.
    echo.
    pause
    exit /b 1
)
echo [1/3] Java: "%JAVA_EXE%"

rem ---------------------------------------------------------------
rem 2) Make sure the application jar exists (build once if missing)
rem ---------------------------------------------------------------
set "APP_JAR="
for %%F in ("%~dp0build\libs\aegis-*.jar") do (
    echo %%~nxF | find "plain" >nul || set "APP_JAR=%%~fF"
)

if not defined APP_JAR (
    echo [2/3] Jar not found - building it once. This may take a few minutes...
    call "%~dp0gradlew.bat" bootJar
    if errorlevel 1 (
        echo.
        echo [ERROR] Build failed. See the messages above.
        echo.
        pause
        exit /b 1
    )
    for %%F in ("%~dp0build\libs\aegis-*.jar") do (
        echo %%~nxF | find "plain" >nul || set "APP_JAR=%%~fF"
    )
)
if not defined APP_JAR (
    echo [ERROR] Could not find the built jar in build\libs.
    echo.
    pause
    exit /b 1
)
echo [2/3] Jar: "%APP_JAR%"

rem ---------------------------------------------------------------
rem 3) Free port 8080, open the dashboard shortly after start, run
rem ---------------------------------------------------------------
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8080" ^| findstr LISTENING') do taskkill /F /PID %%a >nul 2>&1
start "" /b cmd /c "ping -n 16 127.0.0.1 >nul & explorer http://localhost:8080/admin/dashboard"

echo [3/3] Starting server... (browser opens in ~15 seconds)
echo.
"%JAVA_EXE%" -jar "%APP_JAR%"

echo.
echo Server stopped.
pause
endlocal
