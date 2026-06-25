@echo off
chcp 65001 >nul
cd /d "%~dp0"

rem --- Java 21 지정 (이 PC의 포터블 JDK가 있으면 자동 사용) ---
if exist "C:\Users\USER\.jdks\jdk-21.0.11+10\bin\java.exe" set "JAVA_HOME=C:\Users\USER\.jdks\jdk-21.0.11+10"

echo ============================================================
echo   Aegis 보안 시스템을 시작합니다.
echo   - 잠시 후 브라우저(관리자 대시보드)가 자동으로 열립니다.
echo   - 관리자 로그인:   admin  /  admin1234
echo   - 종료: 이 창을 닫거나 Ctrl+C
echo ============================================================
echo.

rem --- 8080 포트를 쓰는 기존 프로세스가 있으면 정리 ---
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8080" ^| findstr LISTENING') do taskkill /F /PID %%a >nul 2>&1

rem --- 서버가 뜬 뒤(약 20초) 브라우저 자동 오픈 ---
start "" /b cmd /c "timeout /t 20 >nul & explorer http://localhost:8080/admin/dashboard"

rem --- 서버 실행 (이 창에서 동작) ---
call gradlew.bat bootRun

echo.
echo 서버가 종료되었습니다. 아무 키나 누르면 창이 닫힙니다.
pause >nul
