@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

echo ========================================
echo Steve AI Mod - Автоматическая сборка и запуск
echo ========================================
echo.

REM Шаг 1: Закрытие всех процессов Minecraft (Java)
echo [1/4] Закрытие всех процессов Minecraft...
taskkill /F /IM java.exe /T >nul 2>nul
if %errorlevel% equ 0 (
    echo ✓ Процессы Minecraft закрыты
) else (
    echo ℹ Процессы Minecraft не найдены или уже закрыты
)
timeout /t 2 /nobreak >nul
echo.

REM Шаг 2: Сборка проекта
echo [2/4] Сборка проекта через Gradle...
call gradlew.bat build
if %errorlevel% neq 0 (
    echo ✗ Ошибка при сборке проекта!
    pause
    exit /b 1
)
echo ✓ Проект успешно собран
echo.

REM Шаг 3: Поиск и копирование JAR файла
echo [3/4] Копирование JAR файла в папку модов...
set "JAR_FILE="
for %%f in (build\libs\steve-ai-mod-*.jar) do (
    set "JAR_FILE=%%f"
)

if not defined JAR_FILE (
    echo ✗ JAR файл не найден в build\libs\
    pause
    exit /b 1
)

set "MODS_DIR=%appdata%\.minecraft\mods"
if not exist "%MODS_DIR%" (
    echo Создание папки модов: %MODS_DIR%
    mkdir "%MODS_DIR%"
)

REM Удаление старых версий мода
echo Удаление старых версий мода...
del /Q "%MODS_DIR%\steve-ai-mod-*.jar" >nul 2>nul

REM Копирование нового JAR
for %%f in ("%JAR_FILE%") do set "JAR_NAME=%%~nxf"
copy /Y "%JAR_FILE%" "%MODS_DIR%\" >nul
if %errorlevel% equ 0 (
    echo ✓ JAR файл скопирован: %MODS_DIR%\!JAR_NAME!
) else (
    echo ✗ Ошибка при копировании JAR файла!
    pause
    exit /b 1
)
echo.

REM Шаг 4: Запуск TLauncher
echo [4/4] Запуск Minecraft через TLauncher...
set "TLAUNCHER_PATH=%APPDATA%\.minecraft\tlauncher.exe"

call :check_tlauncher "%TLAUNCHER_PATH%"
if %errorlevel% equ 0 goto :end_script

REM Если не найден по основному пути, ищем в других местах
echo Попытка найти TLauncher в других местах...
set "TLAUNCHER_FOUND=0"

REM Поиск TLauncher в стандартных местах
if exist "%ProgramFiles%\TLauncher\TLauncher.exe" (
    call :check_tlauncher "%ProgramFiles%\TLauncher\TLauncher.exe"
    if %errorlevel% equ 0 goto :end_script
)

set "PROGRAMFILES_X86=%ProgramFiles(x86)%"
if exist "!PROGRAMFILES_X86!\TLauncher\TLauncher.exe" (
    call :check_tlauncher "!PROGRAMFILES_X86!\TLauncher\TLauncher.exe"
    if %errorlevel% equ 0 goto :end_script
)

if exist "%LOCALAPPDATA%\TLauncher\TLauncher.exe" (
    call :check_tlauncher "%LOCALAPPDATA%\TLauncher\TLauncher.exe"
    if %errorlevel% equ 0 goto :end_script
)

if exist "%USERPROFILE%\AppData\Local\TLauncher\TLauncher.exe" (
    call :check_tlauncher "%USERPROFILE%\AppData\Local\TLauncher\TLauncher.exe"
    if %errorlevel% equ 0 goto :end_script
)

if exist "%APPDATA%\TLauncher\TLauncher.exe" (
    call :check_tlauncher "%APPDATA%\TLauncher\TLauncher.exe"
    if %errorlevel% equ 0 goto :end_script
)

if exist "%USERPROFILE%\Desktop\TLauncher.lnk" (
    call :check_tlauncher "%USERPROFILE%\Desktop\TLauncher.lnk"
    if %errorlevel% equ 0 goto :end_script
)

where TLauncher.exe >nul 2>nul
if %errorlevel% equ 0 (
    call :check_tlauncher "TLauncher.exe"
    if %errorlevel% equ 0 goto :end_script
)

echo ⚠ TLauncher не найден автоматически.
echo Попытка открыть папку с приложениями для ручного запуска...
start "" shell:AppsFolder
echo.
echo Пожалуйста, запустите TLauncher вручную.
goto :end_script

:check_tlauncher
set "TLAUNCHER_EXE=%~1"
if not exist "%TLAUNCHER_EXE%" exit /b 1
echo Найден TLauncher: %TLAUNCHER_EXE%
start "" "%TLAUNCHER_EXE%"
echo ✓ TLauncher запущен
echo.
echo Ожидание загрузки TLauncher (10 секунд)...
timeout /t 10 /nobreak >nul
echo.
echo Автоматический запуск Minecraft...
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0launch_minecraft.ps1"
if %errorlevel% equ 0 (
    echo ✓ Minecraft запускается...
) else (
    echo ⚠ Не удалось автоматически запустить Minecraft, запустите вручную
)
echo.
echo ⚠ ВАЖНО: Для включения консоли отладки в TLauncher:
echo 1. Откройте настройки профиля Minecraft (правой кнопкой на профиле)
echo 2. В разделе "Параметры JVM" или "Дополнительные параметры" добавьте:
echo    -Dforge.logging.console.level=debug
echo    -Dforge.logging.markers=REGISTRIES
echo.
echo После первой настройки консоль отладки будет включаться автоматически.
echo.
exit /b 0

:end_script

echo ========================================
echo Готово! Minecraft должен запуститься через TLauncher.
echo ========================================
echo.

endlocal

