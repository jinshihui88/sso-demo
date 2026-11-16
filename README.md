# sso-demo

Spring Boot 3 + Spring Security 6 + Keycloak 单点登录（SSO）实战项目，配套「统一身份认证中心 + 多业务系统」场景。

两个业务客户端（App-A / App-B）共用同一个 Keycloak Realm 完成 OIDC 登录，登录一次即可访问另一个系统；另有一个 Resource Server 演示后端 API 如何校验 Keycloak 签发的 Access Token。

## 技术栈与版本

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 17 | Spring Boot 3.5 最低要求 |
| Spring Boot | 3.5.16 | 父 POM 统一管理依赖 |
| Spring Security | 6.5.11 | 由 Boot BOM 管理，勿手动指定版本 |
| Keycloak | 26.8.2 | 身份提供方（IdP），需 Java 21+ 运行 |
| 构建工具 | Maven 3.9.3 | 多模块工程 |

## 工程结构

```
sso-demo/
├── pom.xml                     父 POM（模块聚合）
├── docker-compose.yml          Keycloak 容器编排（推荐）
├── keycloak/
│   └── realm-demo.json         Realm / 用户 / 角色 / 客户端 一键导入
├── sso-client/                 浏览器端 OIDC 客户端（App-A / App-B 共用一份代码）
│   └── src/main/java/com/example/ssoclient/
│       ├── SsoClientApplication.java
│       ├── config/SecurityConfig.java           oauth2Login + URL 权限规则
│       ├── config/AuthorityMappingConfig.java   Realm Role → ROLE_ 权限
│       ├── config/OidcLogoutConfig.java         RP-Initiated Logout
│       ├── controller/HomeController.java       可视化首页（含登出表单）
│       ├── controller/UserController.java       /api/me、/api/claims
│       ├── controller/AdminController.java      /admin/**（含方法级鉴权）
│       └── exception/GlobalExceptionHandler.java 授权失败统一 JSON
├── api-service/                Resource Server（JWT 校验）
│   └── src/main/java/com/example/apiservice/
│       ├── ApiServiceApplication.java
│       ├── config/ApiSecurityConfig.java        无状态 + JWT + JSON 401/403
│       ├── config/JwtAuthorityConfig.java       scope / realm_access → 权限
│       └── controller/ApiController.java
├── scripts/
│   ├── run-app-a.bat / run-app-b.bat / run-api.bat   Windows 启动脚本
│   └── sso_smoke_test.py        端到端冒烟测试（16 项断言）
└── docs/
    └── SpringBoot3整合SSO实战教程.md
```

端口分配：Keycloak **8180**、App-A **8081**、App-B **8082**、API **8083**。

## 快速开始

### 1. 启动 Keycloak

方式一（推荐，需要 Docker）：

```bash
docker compose up -d
docker compose logs -f keycloak
```

方式二（本机无 Docker 时，用发行包直接跑）：

```bash
# 下载并解压 keycloak-26.8.2.zip 后进入解压目录，把 realm 导入文件放进去
cp keycloak/realm-demo.json <keycloak目录>/data/import/
# Keycloak 26.x 需要 Java 21+，指定 JAVA_HOME 后启动
JAVA_HOME=<jdk21+目录> KC_BOOTSTRAP_ADMIN_USERNAME=admin \
KC_BOOTSTRAP_ADMIN_PASSWORD='ChangeMe_Admin_123!' \
bin/kc.bat start-dev --http-port=8180 --import-realm
```

确认启动成功：

```bash
curl -s http://localhost:8180/realms/demo/.well-known/openid-configuration | head -c 200
```

管理控制台 <http://localhost:8180>（admin / ChangeMe_Admin_123!）仅本地使用。

### 2. 构建

```bash
mvn clean package
```

### 3. 启动三个应用

App-A（8081）：

```bash
java -jar sso-client/target/sso-client-1.0.0.jar \
  --server.port=8081 \
  --spring.security.oauth2.client.registration.keycloak.client-id=app-a \
  --spring.security.oauth2.client.registration.keycloak.client-secret=app-a-secret-2026 \
  --server.servlet.session.cookie.name=APP_A_SESSION
```

App-B（8082）：

```bash
java -jar sso-client/target/sso-client-1.0.0.jar \
  --server.port=8082 \
  --spring.security.oauth2.client.registration.keycloak.client-id=app-b \
  --spring.security.oauth2.client.registration.keycloak.client-secret=app-b-secret-2026 \
  --server.servlet.session.cookie.name=APP_B_SESSION
```

API（8083）：

```bash
java -jar api-service/target/api-service-1.0.0.jar --server.port=8083
```

也可以直接双击 `scripts/run-app-a.bat`、`run-app-b.bat`、`run-api.bat`（脚本已用环境变量方式配置好参数）。

### 4. 验证

自动化（推荐）：

```bash
python scripts/sso_smoke_test.py
```

预期输出 `合计 16 项，通过 16 项，失败 0 项`。

手工验证：

1. 浏览器打开 <http://localhost:8081>，点「使用 Keycloak 登录」，输入 `demo-user / Test_User_123!`；
2. 登录后访问 <http://localhost:8081/api/me>，能看到 `ROLE_USER`；
3. 同一浏览器打开 <http://localhost:8082>，点登录，**不再要求输入密码**，`/api/me` 是同一个 `sub`；
4. 访问 <http://localhost:8081/admin/ping> 返回 403（demo-user 只有 USER 角色）；
5. 用 `admin-user / Test_Admin_123!` 登录后访问 `/admin/ping` 返回 200；
6. 点首页「登出」，浏览器跳回 Keycloak 登出地址，再访问 App-B 的受保护接口会要求重新登录。

## 接口清单

| 应用 | 接口 | 说明 |
| --- | --- | --- |
| sso-client | `GET /` | 首页，显示当前用户与登出入口 |
| sso-client | `GET /oauth2/authorization/keycloak` | 发起 OIDC 授权（Spring Security 内置） |
| sso-client | `GET /login/oauth2/code/keycloak` | 授权回调（Spring Security 内置） |
| sso-client | `POST /logout` | 本地登出 + 单点登出（需 CSRF Token） |
| sso-client | `GET /api/me` | 当前 OIDC 用户信息 |
| sso-client | `GET /api/claims` | ID Token 声明与权限对比 |
| sso-client | `GET /admin/ping` | 需 ADMIN 角色 |
| sso-client | `GET /admin/report` | 需 ADMIN 角色（`@PreAuthorize`） |
| api-service | `GET /api/public` | 公开接口 |
| api-service | `GET /api/profile` | 需有效 Access Token |
| api-service | `GET /api/admin/health` | 需 ADMIN 角色 |

## 关键配置说明

| 配置项 | 作用 | 注意点 |
| --- | --- | --- |
| `issuer-uri` | 自动发现 OIDC 元数据与 JWKS | 必须与 Realm 的 Discovery issuer 完全一致 |
| `redirect-uri` | 授权回调地址 | 必须与 Keycloak 客户端 Valid redirect URIs 严格匹配（协议/域名/端口/路径） |
| `server.servlet.session.cookie.name` | 本地会话 Cookie 名 | 同机多应用必须区分，否则互相覆盖 |
| `client-authentication-method` | 客户端认证方式 | 服务端 Web 应用用 `client_secret_basic` |
| `post.logout.redirect.uris`（Keycloak 侧） | 登出后允许跳回的地址 | 缺失会导致登出后无法回跳 |

## 踩坑备忘（本项目实测）

1. **同机多应用的 Session Cookie 冲突**：Cookie 不区分端口，App-A 和 App-B 都在 `localhost` 时，默认 `JSESSIONID` 会互相覆盖，表现是「登录完 A，打开 B 又要登录/会话串号」。必须给每个应用设置不同的 `server.servlet.session.cookie.name`。
2. **`@AuthenticationPrincipal OidcUser` 必须显式注解**：不加注解时 Spring MVC 会把 `OidcUser` 当模型属性绑定，抛 `No primary or single unique constructor found`。
3. **`OidcUser#getAuthorities()` 不是最终权限**：它只包含 `OIDC_USER`、`SCOPE_*`；经 `GrantedAuthoritiesMapper` 转换出的 `ROLE_*` 挂在 `Authentication` 上。判断权限要看 `Authentication#getAuthorities()`。
4. **Resource Server 里权限不在 `Jwt` 上**：`Jwt` 只有声明，权限在 `JwtAuthenticationToken`。`@AuthenticationPrincipal JwtAuthenticationToken` 解析不到（principal 是 `Jwt`），应注入 `Authentication` 再强转。
5. **Keycloak 26 在 localhost 会给 Cookie 打 `Secure` 标记**：浏览器把 `http://localhost` 视为安全上下文，所以浏览器能正常回送；但脚本化测试（Python 的 `cookiejar`、部分 HTTP 客户端）会拒绝在 HTTP 上发送，导致登录表单提交 400。测试脚本里需要显式去掉 `Secure` 标记。

## 生产化建议

- 全线 HTTPS，Cookie 设 `Secure` + `HttpOnly` + `SameSite=Lax`，启用 HSTS；
- Keycloak 用生产模式（`start`）+ 外部 PostgreSQL + 固定 hostname，管理端口不对外；
- 每个业务系统独立 Client ID / Secret，密钥放环境变量或密钥管理服务；
- API 增加 audience 校验（Keycloak 侧配置 Audience Mapper + Resource Server 校验 `aud`），避免其他受众的 Token 被误用；
- 多实例部署用 Spring Session + Redis 共享本地会话（注意：这只解决会话共享，不是 SSO 本身）；
- 需要严格单点登出时配置 Back-Channel Logout，并逐个应用验证会话清理。

## 免责说明

`keycloak/realm-demo.json` 中的客户端密钥、测试账号密码仅用于本地演示，禁止用于任何真实环境。
