@echo off
chcp 65001 >nul
setlocal
rem App-A：8081，使用 app-a 客户端与独立 Session Cookie 名
set SERVER_PORT=8081
set OIDC_CLIENT_ID=app-a
set OIDC_CLIENT_SECRET=app-a-secret-2026
set KEYCLOAK_ISSUER_URI=http://localhost:8180/realms/demo
set SESSION_COOKIE_NAME=APP_A_SESSION
set APP_INSTANCE_NAME=App-A
cd /d "%~dp0.."
java -jar sso-client\target\sso-client-1.0.0.jar
endlocal
