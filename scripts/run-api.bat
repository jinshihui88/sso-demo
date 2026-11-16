@echo off
chcp 65001 >nul
setlocal
rem API 服务：8083，校验 Keycloak 签发的 Access Token
set SERVER_PORT=8083
set KEYCLOAK_ISSUER_URI=http://localhost:8180/realms/demo
cd /d "%~dp0.."
java -jar api-service\target\api-service-1.0.0.jar
endlocal
