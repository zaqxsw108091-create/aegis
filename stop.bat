@echo off
chcp 65001 >nul
rem --- 8080 포트(Aegis 서버)를 쓰는 프로세스 종료 ---
set FOUND=0
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8080" ^| findstr LISTENING') do (
    taskkill /F /PID %%a >nul 2>&1
    set FOUND=1
)
echo Aegis 서버를 종료했습니다 (8080 포트 정리 완료).
timeout /t 2 >nul
