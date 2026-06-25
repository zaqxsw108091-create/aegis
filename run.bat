@echo off
cd /d "%~dp0"

rem --- Use portable JDK 21 if present on this PC ---
if exist "C:\Users\USER\.jdks\jdk-21.0.11+10\bin\java.exe" set "JAVA_HOME=C:\Users\USER\.jdks\jdk-21.0.11+10"

echo ============================================================
echo   Aegis security system - starting...
echo   - A browser (admin dashboard) opens automatically.
echo   - Admin login:   admin  /  admin1234
echo   - To stop: close this window or press Ctrl+C
echo ============================================================
echo.

rem --- Free port 8080 if already in use ---
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8080" ^| findstr LISTENING') do taskkill /F /PID %%a >nul 2>&1

rem --- Open browser after server is up (~20s). 'ping' delay works without console input. ---
start "" /b cmd /c "ping -n 21 127.0.0.1 >nul & explorer http://localhost:8080/admin/dashboard"

rem --- Run the server in this window (Ctrl+C or close window to stop) ---
call gradlew.bat bootRun

echo.
echo Server stopped.
pause
