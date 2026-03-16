@echo off
:: NewsAlert Demo Runner
:: =====================
:: Runs the interactive CLI demo against locally running services.
::
:: Pre-requisites (start with docker-compose from project root):
::   docker compose up -d
::   (wait ~30 seconds for services to initialise)
::
:: Then run this script:
::   cd demo
::   run-demo.bat

setlocal

:: Enable ANSI colours on Windows 10+
reg add HKCU\Console /v VirtualTerminalLevel /t REG_DWORD /d 1 /f >nul 2>&1

cd /d "%~dp0"

echo.
echo  [NewsAlert Demo] Compiling and launching...
echo.

mvn --no-transfer-progress compile exec:java -Dexec.mainClass=com.newsalert.demo.NewsAlertDemo

endlocal
