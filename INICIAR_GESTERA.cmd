@echo off
setlocal
cd /d "%~dp0"
title Servidor web Gestera

echo ========================================
echo            INICIAR GESTERA
echo ========================================
echo.
echo Cuando aparezca el mensaje de inicio, abre:
echo http://127.0.0.1:8080
echo.
echo Para detener Gestera presiona Ctrl+C.
echo.

powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0herramientas\ejecutar.ps1"
set "CODIGO_SALIDA=%ERRORLEVEL%"

echo.
if not "%CODIGO_SALIDA%"=="0" echo [ERROR] Gestera termino con un error.
pause
exit /b %CODIGO_SALIDA%
