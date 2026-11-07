@echo off
rem ============================================================================
rem  Movie Backend - setup and run (Windows)
rem
rem  Double-click this file, or run it from a terminal:
rem     setup-and-run.bat            shows a menu
rem     setup-and-run.bat docker     setup + start with Docker (recommended)
rem     setup-and-run.bat local      start without Docker (Java 17, H2 database)
rem     setup-and-run.bat stop       stop the Docker containers
rem     setup-and-run.bat logs       show the backend log
rem ============================================================================
setlocal EnableExtensions
cd /d "%~dp0"
title Movie Backend - setup and run

if /i "%~1"=="docker" goto :docker
if /i "%~1"=="local"  goto :local
if /i "%~1"=="stop"   goto :stop
if /i "%~1"=="logs"   goto :logs

:menu
echo.
echo  ===== Movie Backend =====
echo   1. Setup and start with Docker (recommended)
echo   2. Start without Docker (needs Java 17, uses an H2 file database)
echo   3. Stop the Docker containers
echo   4. Show the backend log
echo   5. Open Swagger UI and the admin portal in the browser
echo   6. Exit
echo.
set "CHOICE="
set /p "CHOICE=Choose 1-6: "
if "%CHOICE%"=="1" goto :docker
if "%CHOICE%"=="2" goto :local
if "%CHOICE%"=="3" goto :stop
if "%CHOICE%"=="4" goto :logs
if "%CHOICE%"=="5" goto :open
if "%CHOICE%"=="6" goto :eof
goto :menu


rem ----------------------------------------------------------------------------
rem  Option 1: Docker
rem ----------------------------------------------------------------------------
:docker
call :check_docker
if errorlevel 1 goto :end
call :ensure_env
call :set_firebase
call :read_env
echo.
echo Building and starting the backend. The first run downloads images and can take a few minutes...
docker compose up --build -d
if errorlevel 1 goto :docker_failed
call :wait_health
if errorlevel 1 goto :not_healthy
call :show_urls
start "" "http://localhost:%API_PORT%/swagger-ui.html"
goto :end

:docker_failed
echo.
echo [ERROR] "docker compose up" failed. Read the messages above.
echo If you see "port is already allocated", set API_PORT or DB_PORT in the .env file (for example API_PORT=8081) and run this file again.
goto :end

:not_healthy
echo.
echo [ERROR] The backend did not become healthy in time. Run this file again and choose 4 to see the log.
goto :end


rem ----------------------------------------------------------------------------
rem  Option 2: without Docker
rem ----------------------------------------------------------------------------
:local
java -version >nul 2>&1
if errorlevel 1 goto :no_java
call :ensure_env
call :load_env
rem The .env path for Firebase is a path inside Docker, so use the real file here (or none).
set "FIREBASE_CREDENTIALS_PATH="
set "FBFILE="
for %%f in (secrets\*.json) do if not defined FBFILE set "FBFILE=%%~nxf"
if defined FBFILE set "FIREBASE_CREDENTIALS_PATH=%CD%\secrets\%FBFILE%"
if not defined API_PORT set "API_PORT=8080"
echo.
echo Starting the backend with the local profile on port %API_PORT%. Press Ctrl+C to stop it.
echo Swagger UI will open in your browser in about 40 seconds.
start "" cmd /c "timeout /t 40 /nobreak >nul & start http://localhost:%API_PORT%/swagger-ui.html"
call gradlew.bat bootRun --args="--spring.profiles.active=local --server.port=%API_PORT%"
goto :end

:no_java
echo.
echo [ERROR] Java was not found. Install Java 17 (for example from https://adoptium.net) and run this file again.
goto :end


rem ----------------------------------------------------------------------------
rem  Options 3, 4 and 5
rem ----------------------------------------------------------------------------
:stop
call :check_docker
if errorlevel 1 goto :end
docker compose down
goto :end

:logs
call :check_docker
if errorlevel 1 goto :end
docker compose logs -f api
goto :end

:open
if not exist ".env" goto :open_default
call :read_env
goto :open_now
:open_default
set "API_PORT=8080"
:open_now
start "" "http://localhost:%API_PORT%/swagger-ui.html"
start "" "http://localhost:%API_PORT%/admin/movies"
goto :end

:end
echo.
pause
goto :eof


rem ============================================================================
rem  Helpers
rem ============================================================================

rem --- Docker must be installed and running --------------------------------
:check_docker
docker --version >nul 2>&1
if errorlevel 1 goto :no_docker
docker compose version >nul 2>&1
if errorlevel 1 goto :no_compose
docker info >nul 2>&1
if errorlevel 1 goto :docker_not_running
exit /b 0
:no_docker
echo.
echo [ERROR] Docker was not found. Install Docker Desktop from https://www.docker.com/products/docker-desktop and run this file again.
exit /b 1
:no_compose
echo.
echo [ERROR] "docker compose" is not available. Update Docker Desktop to a recent version.
exit /b 1
:docker_not_running
echo.
echo [ERROR] Docker is installed but not running. Start Docker Desktop, wait until it says it is running, then run this file again.
exit /b 1

rem --- Create .env from .env.example the first time, and ask for the TMDb key --
:ensure_env
if exist ".env" exit /b 0
copy /y ".env.example" ".env" >nul
echo.
echo Created the .env settings file from .env.example.
echo A TMDb API key gives you real movies. Press Enter to skip and use 25 sample movies.
echo (How to get a key: see section 2 of README.md)
set "TMDB_KEY="
set /p "TMDB_KEY=TMDb API key (v3): "
if defined TMDB_KEY call :save_tmdb_key
exit /b 0

:save_tmdb_key
powershell -NoProfile -Command "(Get-Content '.env') -replace '^TMDB_API_KEY=.*', ('TMDB_API_KEY=' + $env:TMDB_KEY) | Set-Content -Encoding ASCII '.env'"
echo TMDb key saved in .env
exit /b 0

rem --- Use the first Firebase service-account file in "secrets" for Docker ----
:set_firebase
set "FBFILE="
for %%f in (secrets\*.json) do if not defined FBFILE set "FBFILE=%%~nxf"
if not defined FBFILE goto :no_firebase_file
powershell -NoProfile -Command "(Get-Content '.env') -replace '^FIREBASE_CREDENTIALS_PATH=\s*$', ('FIREBASE_CREDENTIALS_PATH=/secrets/' + $env:FBFILE) | Set-Content -Encoding ASCII '.env'"
echo Firebase file: secrets\%FBFILE% (push notifications will be sent)
exit /b 0
:no_firebase_file
echo No Firebase file in the "secrets" folder, so push notifications will only be logged.
exit /b 0

rem --- Read API_PORT from .env (default 8080) -------------------------------
:read_env
set "API_PORT=8080"
for /f "usebackq eol=# tokens=1,* delims==" %%a in (".env") do if /i "%%a"=="API_PORT" set "API_PORT=%%b"
exit /b 0

rem --- Load every KEY=VALUE line of .env into this session -----------------
:load_env
call :read_env
for /f "usebackq eol=# tokens=1,* delims==" %%a in (".env") do set "%%a=%%b"
exit /b 0

rem --- Wait until /actuator/health says UP (up to about 2 minutes) ---------
:wait_health
echo Waiting for the backend to start...
set /a TRIES=0
:wait_loop
powershell -NoProfile -Command "try { (Invoke-WebRequest -UseBasicParsing 'http://localhost:%API_PORT%/actuator/health').Content } catch { '' }" | findstr /c:"UP" >nul
if not errorlevel 1 exit /b 0
set /a TRIES+=1
if %TRIES% geq 40 exit /b 1
timeout /t 3 /nobreak >nul
goto :wait_loop

:show_urls
echo.
echo ===================== The backend is running =====================
echo   Swagger UI    http://localhost:%API_PORT%/swagger-ui.html
echo   API           http://localhost:%API_PORT%/api/movies
echo   Admin portal  http://localhost:%API_PORT%/admin/movies   (admin / admin123)
echo   Android app   http://10.0.2.2:%API_PORT%/api/   (from the emulator)
echo.
echo   Stop it:  setup-and-run.bat stop
echo   Log:      setup-and-run.bat logs
echo ==================================================================
exit /b 0
