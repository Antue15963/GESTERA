@echo off
setlocal
cd /d "%~dp0"
title Compilar Gestera

echo ========================================
echo          COMPILADOR DE GESTERA
echo ========================================
echo.

powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0herramientas\compilar.ps1"
set "CODIGO_SALIDA=%ERRORLEVEL%"

echo.
if "%CODIGO_SALIDA%"=="0" (
    echo [CORRECTO] Gestera fue compilado.
    echo Archivo: %~dp0distribucion\gestera.jar
) else (
    echo [ERROR] No se pudo compilar Gestera.
    echo Revisa el mensaje mostrado arriba.
)
echo.
pause
exit /b %CODIGO_SALIDA%
