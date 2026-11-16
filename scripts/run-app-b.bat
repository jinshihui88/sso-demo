@echo off
chcp 65001 >nul
setlocal
rem App-B：8082，使用 app-b 客户端与独立 Session Cookie 名
set SERVER_PORT=8082
set OIDC_CLIENT_ID=app-b
set OIDC_CLIENT_SECRET=app-b-secret-2026
set KEYCLOAK_ISSUER_URI=http://localhost:8180/realms/demo
set SESSION_COOKIE_NAME=APP_B_SESSION
set APP_INSTANCE_NAME=App-B
cd /d "%~dp0.."
java -jar sso-client\target\sso-client-1.0.0.jar
endlocal
