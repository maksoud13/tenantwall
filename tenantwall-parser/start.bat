@echo off
REM Start the TenantWall parser. Defaults to 127.0.0.1 (localhost-only).
REM For Docker/K8s: set TENANTWALL_PARSER_BIND_HOST=0.0.0.0 explicitly.

setlocal
if "%TENANTWALL_PARSER_BIND_HOST%"=="" (set HOST=127.0.0.1) else (set HOST=%TENANTWALL_PARSER_BIND_HOST%)
if "%TENANTWALL_PARSER_PORT%"=="" (set PORT=8000) else (set PORT=%TENANTWALL_PARSER_PORT%)

echo =========================================================
echo   TenantWall Parser
echo   Bind: %HOST%:%PORT%
if "%HOST%"=="0.0.0.0" (
    echo   WARNING: bound to 0.0.0.0 - exposed to the network.
    echo   Ensure a firewall + reverse proxy with auth is in place.
)
echo =========================================================

uvicorn app.main:app --host %HOST% --port %PORT%
endlocal