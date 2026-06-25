@echo off
rem --- Stop the Aegis server (process listening on port 8080) ---
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8080" ^| findstr LISTENING') do taskkill /F /PID %%a >nul 2>&1
echo Aegis server stopped (port 8080 freed).
timeout /t 2 >nul
