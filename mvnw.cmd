@echo off
where mvn >nul 2>nul
if %ERRORLEVEL% EQU 0 (
  mvn %*
  exit /b %ERRORLEVEL%
)
echo Maven is not installed. Please install Maven 3.9+ or use WSL/Linux mvnw.
exit /b 1
