#!/usr/bin/env python3
"""
SSO 全链路冒烟测试（仅用标准库，模拟浏览器行为）。

覆盖场景：
  1. App-A 首次访问受保护接口 -> 302 到 Keycloak
  2. 提交账号密码完成登录 -> 回调 App-A -> 本地 Session 建立 -> /api/me 返回身份
  3. 同一浏览器访问 App-B -> 不再要求输入密码（单点登录生效）-> /api/me 返回同一用户
  4. 普通用户访问 /admin/** -> 403
  5. admin-user 登录后访问 /admin/** -> 200
  6. 登出 -> 302 到 Keycloak end_session_endpoint（单点登出）
  7. Resource Server：无 Token 401、无效 Token 401、普通用户访问管理接口 403、管理员 200

用法：python scripts/sso_smoke_test.py
前置：Keycloak(8180) / App-A(8081) / App-B(8082) / API(8083) 均已启动
"""

import html
import http.cookiejar
import json
import re
import sys
import urllib.error
import urllib.parse
import urllib.request

KEYCLOAK = "http://localhost:8180"
REALM = "demo"
APP_A = "http://localhost:8081"
APP_B = "http://localhost:8082"
API = "http://localhost:8083"

DEMO_USER = ("demo-user", "Test_User_123!")
ADMIN_USER = ("admin-user", "Test_Admin_123!")
API_CLIENT = ("api-cli", "api-cli-secret-2026")

results = []


class NoRedirect(urllib.request.HTTPRedirectHandler):
    """禁止自动跟随跳转，便于断言每一步的 Location。"""

    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class Browser:
    """带 Cookie 的极简浏览器。"""

    def __init__(self):
        self.jar = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(
            urllib.request.ProxyHandler({}),          # 绕开系统代理，避免 localhost 走代理
            urllib.request.HTTPCookieProcessor(self.jar),
            NoRedirect,
        )

    def request(self, url, data=None, headers=None):
        req = urllib.request.Request(url, data=data, headers=headers or {})
        try:
            with self.opener.open(req, timeout=20) as resp:
                self._relax_secure_cookies()
                return resp.status, dict(resp.headers), resp.read().decode("utf-8", "replace")
        except urllib.error.HTTPError as e:
            body = e.read().decode("utf-8", "replace")
            self._relax_secure_cookies()
            return e.code, dict(e.headers), body

    def _relax_secure_cookies(self):
        """
        Keycloak 26 会给 Cookie 打上 Secure 标记；浏览器把 http://localhost 当作安全上下文，
        因此浏览器能正常回送，但 Python 的 cookiejar 会严格拒绝在 HTTP 上发送 Secure Cookie。
        这里去掉标记，模拟浏览器在 localhost 上的实际行为。仅用于本地 HTTP 实验。
        """
        for cookie in self.jar:
            if cookie.secure:
                cookie.secure = False

    def get(self, url):
        return self.request(url)

    def post_form(self, url, fields):
        data = urllib.parse.urlencode(fields).encode()
        return self.request(url, data=data,
                            headers={"Content-Type": "application/x-www-form-urlencoded"})


def check(name, ok, detail=""):
    results.append((name, ok, detail))
    print(("  [PASS] " if ok else "  [FAIL] ") + name + (f"  -> {detail}" if detail else ""))


def json_of(text):
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        return {}


def login(browser, app_base, username, password):
    """走完整授权码流程：入口 -> Keycloak 登录页 -> 提交凭据 -> 回调应用。"""
    status, headers, _ = browser.get(f"{app_base}/oauth2/authorization/keycloak")
    if status != 302:
        return False, f"授权入口未返回 302（实际 {status}）"
    authorize_url = headers.get("Location", "")

    status, headers, body = browser.get(authorize_url)
    if status == 302:
        # Keycloak 已有 SSO 会话，直接带 code 回调：属于单点登录的典型表现
        return follow_callback(browser, headers.get("Location", ""), reused_session=True)

    if status != 200:
        return False, f"Keycloak 授权页异常（{status}）"

    # 精确取登录表单（页面上可能还有语言切换等其他表单）
    match = re.search(r'<form[^>]*id="kc-form-login"[^>]*action="([^"]+)"', body) \
        or re.search(r'<form[^>]+action="([^"]+)"', body)
    if not match:
        return False, "未在 Keycloak 登录页中找到表单 action"
    action = html.unescape(match.group(1))

    status, headers, _ = browser.post_form(
        action, {"username": username, "password": password, "credentialId": ""})
    if status != 302:
        return False, f"提交凭据后未跳转（{status}），可能是密码错误"
    location = headers.get("Location", "")
    if "code=" not in location:
        return False, f"回调地址中没有授权码：{location[:120]}"
    return follow_callback(browser, location, reused_session=False)


def follow_callback(browser, callback_url, reused_session):
    status, headers, _ = browser.get(callback_url)
    if status not in (302, 200):
        return False, f"回调处理失败（{status}）"
    note = "复用 Keycloak 会话" if reused_session else "输入密码登录"
    return True, note


def password_grant(username, password):
    """用 api-cli 客户端走 password 授权拿 Access Token（仅本地测试用途）。"""
    data = urllib.parse.urlencode({
        "grant_type": "password",
        "client_id": API_CLIENT[0],
        "client_secret": API_CLIENT[1],
        "username": username,
        "password": password,
        "scope": "openid profile email",
    }).encode()
    req = urllib.request.Request(
        f"{KEYCLOAK}/realms/{REALM}/protocol/openid-connect/token",
        data=data, headers={"Content-Type": "application/x-www-form-urlencoded"})
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    with opener.open(req, timeout=20) as resp:
        return json.loads(resp.read().decode())


def api_get(path, token=None):
    headers = {"Authorization": f"Bearer {token}"} if token else {}
    req = urllib.request.Request(API + path, headers=headers)
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    try:
        with opener.open(req, timeout=20) as resp:
            return resp.status, json_of(resp.read().decode("utf-8", "replace"))
    except urllib.error.HTTPError as e:
        return e.code, json_of(e.read().decode("utf-8", "replace"))


def main():
    print("== 1. 浏览器端 OIDC 登录（App-A） ==")
    browser = Browser()
    status, headers, _ = browser.get(f"{APP_A}/api/me")
    check("未登录访问 App-A 受保护接口返回 302", status == 302, f"HTTP {status}")

    ok, detail = login(browser, APP_A, *DEMO_USER)
    check("demo-user 完成授权码登录", ok, detail)

    status, _, body = browser.get(f"{APP_A}/api/me")
    me_a = json_of(body)
    check("App-A /api/me 返回当前用户", status == 200 and me_a.get("username") == DEMO_USER[0],
          f"HTTP {status}, username={me_a.get('username')}, authorities={me_a.get('authorities')}")

    print("== 2. 单点登录（App-B） ==")
    ok, detail = login(browser, APP_B, *DEMO_USER)
    check("访问 App-B 未再次输入密码", ok and detail == "复用 Keycloak 会话", detail)

    status, _, body = browser.get(f"{APP_B}/api/me")
    me_b = json_of(body)
    check("App-B /api/me 返回同一用户", status == 200 and me_b.get("subject") == me_a.get("subject"),
          f"HTTP {status}, subject 一致={me_b.get('subject') == me_a.get('subject')}")

    print("== 3. 权限控制（客户端） ==")
    status, _, body = browser.get(f"{APP_A}/admin/ping")
    check("USER 访问 /admin/ping 返回 403", status == 403, f"HTTP {status}")

    admin_browser = Browser()
    ok, detail = login(admin_browser, APP_A, *ADMIN_USER)
    check("admin-user 完成登录", ok, detail)
    status, _, body = admin_browser.get(f"{APP_A}/admin/ping")
    check("ADMIN 访问 /admin/ping 返回 200", status == 200, f"HTTP {status}")
    status, _, body = admin_browser.get(f"{APP_A}/admin/report")
    check("ADMIN 访问方法级鉴权 /admin/report 返回 200", status == 200, f"HTTP {status}")

    print("== 4. 单点登出 ==")
    _, _, page = admin_browser.get(f"{APP_A}/")
    token_match = re.search(r'name="_csrf"\s+value="([^"]+)"', page)
    if token_match:
        status, headers, _ = admin_browser.post_form(
            f"{APP_A}/logout", {"_csrf": token_match.group(1)})
        location = headers.get("Location", "")
        check("登出后跳转 Keycloak end_session_endpoint",
              status == 302 and "/protocol/openid-connect/logout" in location,
              f"HTTP {status}")
    else:
        check("登出", False, "首页未找到 CSRF Token")

    print("== 5. Resource Server JWT 鉴权 ==")
    status, body = api_get("/api/public")
    check("公开接口免 Token 访问 200", status == 200, f"HTTP {status}")

    status, body = api_get("/api/profile")
    check("无 Token 访问 /api/profile 返回 401", status == 401, f"HTTP {status}")

    status, body = api_get("/api/profile", "not-a-jwt")
    check("无效 Token 返回 401", status == 401, f"HTTP {status}")

    demo_token = password_grant(*DEMO_USER)["access_token"]
    status, body = api_get("/api/profile", demo_token)
    check("有效 Access Token 访问 /api/profile 返回 200", status == 200,
          f"HTTP {status}, subject={body.get('subject')}, authorities={body.get('authorities')}")

    status, body = api_get("/api/admin/health", demo_token)
    check("USER 令牌访问管理接口返回 403", status == 403, f"HTTP {status}")

    admin_token = password_grant(*ADMIN_USER)["access_token"]
    status, body = api_get("/api/admin/health", admin_token)
    check("ADMIN 令牌访问管理接口返回 200", status == 200, f"HTTP {status}")

    failed = [r for r in results if not r[1]]
    print(f"\n合计 {len(results)} 项，通过 {len(results) - len(failed)} 项，失败 {len(failed)} 项")
    if failed:
        for name, _, detail in failed:
            print(f"  FAIL: {name} ({detail})")
        return 1
    print("全部通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
