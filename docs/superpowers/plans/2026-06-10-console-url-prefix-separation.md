# 前后端 URL 路径空间分离 — 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将前端 SPA 路由统一到 `/console/*` 前缀下，后端 API 统一到 `/api/*` 前缀下，消除前后端 URL 冲突，删除 `SpaRoutingFilter`。

**Architecture:** 后端通过 `WebMvcConfigurer.configurePathMatch()` 给所有控制器自动加 `/api` 前缀（控制器代码零改动）；前端通过 ICE.js `router.basename` 配置自动给路由加 `/console` 前缀（路由定义零改动）。前后端路径空间完全分离后，SPA 路由 fallback 逻辑大幅简化。

**Tech Stack:** Java 8 / Spring Boot 2.7.18 / ICE.js 3.x / React 18

---

## 文件结构

| 操作 | 文件 | 职责 |
|---|---|---|
| 修改 | `backend/console/.../WebMvcInitializer.java` | API 前缀 + SPA fallback + 根路径重定向 |
| 删除 | `backend/console/.../filter/SpaRoutingFilter.java` | 不再需要 Accept 头检测 |
| 修改 | `backend/console/.../controller/Oauth2Controller.java` | 回调/重定向 URL 更新 |
| 修改 | `backend/console/.../resources/application.properties` | springdoc 路径 |
| 修改 | `frontend/ice.config.mts` | basename + proxy |
| 修改 | `frontend/src/services/request.tsx` | baseURL + 401 跳转 |
| 修改 | `frontend/src/pages/login/index.tsx` | SSO + replaceState + redirect |
| 修改 | `frontend/src/pages/init/index.tsx` | window.location.href |
| 修改 | `helm/templates/deployment.yaml` | 健康检查路径 |

---

### Task 1: 后端 — 改造 WebMvcInitializer

**Files:**
- Modify: `backend/console/src/main/java/com/alibaba/higress/console/WebMvcInitializer.java`

- [ ] **Step 1: 添加 configurePathMatch 方法 + 简化 addResourceHandlers + 添加根路径重定向**

将 `WebMvcInitializer.java` 的全部内容替换为：

```java
/*
 * Copyright (c) 2022-2023 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.alibaba.higress.console;

import java.time.Duration;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;
import org.springframework.web.servlet.resource.ResourceResolverChain;

@Configuration
@EnableWebMvc
public class WebMvcInitializer implements WebMvcConfigurer {

    private static final String HOMEPAGE_PATH = "/index.html";

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix("/api", c ->
            c.isAnnotationPresent(org.springframework.web.bind.annotation.RestController.class)
        );
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/", "/console/");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**").addResourceLocations("classpath:/static/")
            .setCacheControl(CacheControl.maxAge(Duration.ZERO).mustRevalidate()).setUseLastModified(true)
            .resourceChain(true).addResolver(new PathResourceResolver() {
                @Override
                protected Resource resolveResourceInternal(HttpServletRequest request, @NonNull String requestPath,
                    @NonNull List<? extends Resource> locations, @NonNull ResourceResolverChain chain) {
                    Resource resource = super.resolveResourceInternal(request, requestPath, locations, chain);
                    if (resource == null && requestPath.startsWith("console")) {
                        // Frontend SPA route fallback to index.html
                        resource = super.resolveResourceInternal(request, HOMEPAGE_PATH, locations, chain);
                    }
                    return resource;
                }
            });
    }
}
```

关键变化：
- **新增 `configurePathMatch()`**：给所有 `@RestController` 自动加 `/api` 前缀。控制器代码无需改动，运行时 `/user` 变为 `/api/user`，`/v1/routes` 变为 `/api/v1/routes`
- **新增 `addViewControllers()`**：根路径 `/` 重定向到 `/console/`
- **简化 `addResourceHandlers()`**：
  - `/*` 改为 `/**`（匹配多段路径如 `/console/user/list`）
  - 删除 `API_PATH_PREFIXES` 和 `STATIC_RESOURCE_DIR_PREFIXES`（不再需要）
  - 只对 `console` 开头的路径做 SPA fallback
  - 删除 `isStaticResourceRequest()` 方法

- [ ] **Step 2: 编译验证**

Run: `cd backend && mvn compile -pl console -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add backend/console/src/main/java/com/alibaba/higress/console/WebMvcInitializer.java
git commit -m "refactor: WebMvcInitializer 添加 /api 前缀、简化 SPA fallback、添加根路径重定向"
```

---

### Task 2: 后端 — 删除 SpaRoutingFilter

**Files:**
- Delete: `backend/console/src/main/java/com/alibaba/higress/console/filter/SpaRoutingFilter.java`

- [ ] **Step 1: 删除文件**

```bash
rm backend/console/src/main/java/com/alibaba/higress/console/filter/SpaRoutingFilter.java
```

- [ ] **Step 2: 确认无其他引用**

Run: `cd backend && grep -r "SpaRoutingFilter" --include="*.java" --include="*.xml" --include="*.properties" .`
Expected: 无输出（无引用）

- [ ] **Step 3: 编译验证**

Run: `cd backend && mvn compile -pl console -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add -u backend/console/src/main/java/com/alibaba/higress/console/filter/SpaRoutingFilter.java
git commit -m "refactor: 删除 SpaRoutingFilter，前后端路径空间已完全分离"
```

---

### Task 3: 后端 — 更新 Oauth2Controller 重定向 URL

**Files:**
- Modify: `backend/console/src/main/java/com/alibaba/higress/console/controller/Oauth2Controller.java`

- [ ] **Step 1: 更新 `buildRedirectUri` 方法（第 130 行）**

将回调路径从 `/oauth2/callback/` 改为 `/api/oauth2/callback/`：

```java
    private String buildRedirectUri(HttpServletRequest request, String providerKey) {
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int port = request.getServerPort();
        StringBuilder sb = new StringBuilder(scheme).append("://").append(serverName);
        if ("http".equals(scheme) && port != 80 || "https".equals(scheme) && port != 443) {
            sb.append(":").append(port);
        }
        sb.append("/api/oauth2/callback/").append(providerKey);
        return sb.toString();
    }
```

- [ ] **Step 2: 更新 `sendSuccessRedirect` 方法（第 134 行）**

将默认重定向从 `/` 改为 `/console/`：

```java
    private void sendSuccessRedirect(HttpServletResponse response) {
        try {
            String target = StringUtils.isNotEmpty(redirectBaseUrl) ? redirectBaseUrl + "/console/" : "/console/";
            response.sendRedirect(target);
        } catch (Exception e) {
            throw new BusinessException("Failed to redirect after login", e);
        }
    }
```

- [ ] **Step 3: 更新 `redirectToLoginWithError` 方法（第 143 行）**

将登录失败重定向路径更新：

```java
    private void redirectToLoginWithError(HttpServletResponse response, String error) {
        try {
            String encodedError = URLEncoder.encode(error, "UTF-8");
            String loginPath = "/console/login?oauth_error=" + encodedError;
            String target = StringUtils.isNotEmpty(redirectBaseUrl)
                ? redirectBaseUrl + loginPath : loginPath;
            response.sendRedirect(target);
        } catch (Exception e) {
            throw new BusinessException("Failed to redirect", e);
        }
    }
```

- [ ] **Step 4: 编译验证**

Run: `cd backend && mvn compile -pl console -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add backend/console/src/main/java/com/alibaba/higress/console/controller/Oauth2Controller.java
git commit -m "refactor: Oauth2Controller 重定向 URL 适配 /api 和 /console 前缀"
```

---

### Task 4: 后端 — 更新 application.properties

**Files:**
- Modify: `backend/console/src/main/resources/application.properties`

- [ ] **Step 1: 更新 springdoc 路径匹配**

将第 14 行：
```properties
springdoc.pathsToMatch=/v1/**,/session/**,/dashboard/**,/system/**,/user/**,/oauth2/**
```
改为：
```properties
springdoc.pathsToMatch=/api/**
```

- [ ] **Step 2: Commit**

```bash
git add backend/console/src/main/resources/application.properties
git commit -m "refactor: springdoc 路径匹配更新为 /api/**"
```

---

### Task 5: 前端 — 更新 ICE.js 配置

**Files:**
- Modify: `frontend/ice.config.mts`
- Modify: `frontend/src/app.ts`

- [ ] **Step 1: 更新 ice.config.mts 的 proxy 配置**

⚠️ **重要：** ICE.js 3.x 的 `ice.config.mts` **不支持** `router` 键（会报 `Config key 'router' is not supported`）。basename 必须配置在 `app.ts` 的 `defineAppConfig` 中。

在 `ice.config.mts` 中只更新 proxy 配置，去掉 `pathRewrite`：

```ts
  proxy: {
    "/api": {
      target: "http://localhost:8080",
      changeOrigin: true,
    },
  },
```

- [ ] **Step 2: 在 app.ts 的 defineAppConfig 中添加 basename**

修改 `frontend/src/app.ts`：

```ts
// App config, see https://v3.ice.work/docs/guide/basic/app
export default defineAppConfig(() => ({
  router: {
    basename: '/console',
  },
}));
```

框架通过 `getRouterBasename()` 读取 `appConfig.router.basename`（见 `@ice/app/esm/utils/getRouterBasename.js`）。所有前端路由自动加 `/console` 前缀。

- [ ] **Step 3: Commit**

```bash
git add frontend/ice.config.mts frontend/src/app.ts
git commit -m "fix: basename 配置移至 app.ts defineAppConfig（ice.config.mts 不支持 router 键）"
```

关键变化：
- **新增 `router.basename: '/console'`（在 app.ts）**：所有前端路由自动加 `/console` 前缀，路由定义文件无需改动
- **proxy 去掉 `pathRewrite`**：后端控制器已通过 `configurePathMatch` 自带 `/api` 前缀，proxy 直接透传 `/api/xxx`

- [ ] **Step 2: Commit**

```bash
git add frontend/ice.config.mts
git commit -m "refactor: ICE.js 配置添加 basename /console，proxy 去掉 pathRewrite"
```

---

### Task 6: 前端 — 更新 request.tsx

**Files:**
- Modify: `frontend/src/services/request.tsx`

- [ ] **Step 1: 统一 baseURL + 更新 401 重定向路径**

将 `request.tsx` 完整替换为：

```tsx
import { Modal } from "antd";
import axios from "axios";
import i18next from 'i18next';
import { ErrorComp } from './exception';

const request = axios.create({
  timeout: 5 * 1000,
  baseURL: "/api",
  headers: {
    "Content-Type": "application/json",
  },
});

request.interceptors.request.use((config) => {
  const token = localStorage.getItem("token");
  if (token) {
    config.headers = {
      Authorization: token,
      ...config.headers,
    };
  }
  if (config.method && config.method.toUpperCase() === 'GET' && config.url) {
    config.url = `${config.url}${config.url.indexOf('?') === -1 ? '?' : '&'}ts=${Date.now()}`;
  }
  return config;
});

request.interceptors.response.use(
  (response) => {
    const { status, config, data } = response;

    // console.log("response====", response);
    const statusCategory = Math.floor(status / 100);
    if (statusCategory === 2) {
      if (data && data.data) {
        return Promise.resolve(data.data);
      }
      return Promise.resolve(data);
    }
    return Promise.resolve(response);
  },
  (error) => {
    // console.log("error====", error);
    let { message, config, code } = error;
    if (error.response) {
      const { status, data } = error.response;

      if (status === 401) {
        if (config.url.indexOf('/login') !== -1) {
          // Unauthorized response is allowed for a login request.
          Promise.resolve(error.response);
          return;
        }
        // Unauthorized. Jump to the login page.
        Promise.reject(error);
        if (window.location.href.indexOf('/init') === -1 && window.location.href.indexOf('/login') === -1) {
          // Strip /console prefix so redirect param is react-router compatible (without basename)
          const pathname = window.location.pathname;
          const redirectPath = pathname.startsWith('/console') ? pathname.slice('/console'.length) || '/' : pathname;
          window.location.href = `/console/login?redirect=${encodeURIComponent(redirectPath)}`;
        }
        return;
      }
      const messageKeys = [`request.error.${status}_${config.method}`, `request.error.${status}`];
      for (const key of messageKeys) {
        const localizedMessage = i18next.t(key);
        if (localizedMessage !== key) {
          message = localizedMessage;
          break;
        }
      }
      code = status;
      if (data) {
        config.data = typeof data === 'string' ? data : JSON.stringify(data);
      }
    }
    showErrorModal(message, config, code);
    return Promise.reject(error);
  },
);

function showErrorModal(message: string, config: object, code?: number) {
  Modal.warning({
    title: i18next.t('misc.error'),
    content: <ErrorComp content={message} options={config} code={code} />,
    okText: i18next.t('misc.close'),
    width: 560,
  });
}

export default request;
```

关键变化：
- **`baseURL` 统一为 `"/api"`**：去掉 `process.env.ICE_CORE_MODE` 判断，开发和生产一致
- **401 跳转路径**：从 `/login?redirect=...` 改为 `/console/login?redirect=...`
- **redirect 参数**：从 `window.location.pathname` 中去掉 `/console` 前缀，确保与 react-router 的 basename 机制兼容
- **`indexOf('/login')` 和 `indexOf('/init')` 检查**：无需修改，因为 `/console/login` 包含子串 `/login`

- [ ] **Step 2: Commit**

```bash
git add frontend/src/services/request.tsx
git commit -m "refactor: request.tsx 统一 baseURL 为 /api，更新 401 重定向路径"
```

---

### Task 7: 前端 — 更新 login/index.tsx

**Files:**
- Modify: `frontend/src/pages/login/index.tsx`

- [ ] **Step 1: 更新 SSO 跳转 + replaceState 路径**

需要修改 3 处：

**第 40 行** — `window.history.replaceState` 路径：
```tsx
// 旧：
window.history.replaceState({}, '', '/login');
// 新：
window.history.replaceState({}, '', '/console/login');
```

**第 72 行** — redirect 检查（无需修改，因为 redirect 参数现在不含 `/console` 前缀）：
```tsx
if (!redirectUrl || redirectUrl === '/login') {
  redirectUrl = '/';
}
```

**第 82-84 行** — SSO 跳转，去掉 dev/prod 判断：
```tsx
// 旧：
function handleSsoLogin(providerKey: string) {
  const prefix = process.env.ICE_CORE_MODE === "development" ? "/api" : "";
  window.location.href = `${prefix}/oauth2/authorization/${providerKey}`;
}
// 新：
function handleSsoLogin(providerKey: string) {
  window.location.href = `/api/oauth2/authorization/${providerKey}`;
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/pages/login/index.tsx
git commit -m "refactor: login 页面 SSO 跳转统一 /api 前缀，更新 replaceState 路径"
```

---

### Task 8: 前端 — 更新 init/index.tsx

**Files:**
- Modify: `frontend/src/pages/init/index.tsx`

- [ ] **Step 1: 更新 window.location.href 路径**

修改第 40 行：

```tsx
// 旧：
window.location.href = '/login';
// 新：
window.location.href = '/console/login';
```

其他路径无需修改：
- 第 25 行 `navigate('/', { replace: true })` — basename 自动处理 ✅

- [ ] **Step 2: Commit**

```bash
git add frontend/src/pages/init/index.tsx
git commit -m "refactor: init 页面跳转路径适配 /console 前缀"
```

---

### Task 9: 前端 — 确认无需改动的文件

以下文件使用了 `history?.push()` 或 `navigate()` 进行路由跳转，由于 ICE.js `basename` 自动处理前缀，**无需修改**。逐个确认：

| 文件 | 调用 | 为什么无需改动 |
|---|---|---|
| `AvatarDropdown/index.tsx:24-26` | `history?.push({ pathname: '/login' })` | basename 自动加 `/console` 前缀 |
| `AvatarDropdown/index.tsx:36` | `history?.push('/user/changePassword')` | basename 自动处理 |
| `changePassword.tsx:38` | `history?.push('/login')` | basename 自动处理 |
| `404.tsx:24` | `history?.push('/')` | basename 自动处理 → `/console/` |
| `plugin/index.tsx:18-20` | `QUERY_TYPE_2_BACK_PATH` 映射 | 用 `history?.push()` 调用，basename 自动处理 |
| `mcp/detail.tsx:107,214` | `navigate('/mcp/list')` | basename 自动处理 |
| `mcp/list.tsx:77,95` | `history?.push('/mcp/detail?...')` | basename 自动处理 |
| `route/index.tsx:245` | `history?.push('/route/config?...')` | basename 自动处理 |
| `domain/index.tsx:145` | `history?.push('/domain/config?...')` | basename 自动处理 |
| `ai/route.tsx:245` | `history?.push('/ai/route/config?...')` | basename 自动处理 |
| `_defaultProps.tsx` | 所有路由 path 定义 | basename 自动加前缀 |
| `index.tsx:12` | `navigate(redirectTarget, ...)` | basename 自动处理 |
| `exception.tsx:43` | `options.baseURL` 展示 | 新 baseURL `/api` 自动正确显示 |

> **验证方式：** 实现完成后在开发模式下逐个页面点击跳转，确认所有导航正常。

---

### Task 10: 部署 — 更新 Helm 健康检查

**Files:**
- Modify: `helm/templates/deployment.yaml`

- [ ] **Step 1: 更新 readinessProbe 路径**

将 `helm/templates/deployment.yaml` 第 103-106 行：
```yaml
          readinessProbe:
            httpGet:
              path: /
              port: http
```
改为：
```yaml
          readinessProbe:
            httpGet:
              path: /api/healthz
              port: http
```

- [ ] **Step 2: Commit**

```bash
git add helm/templates/deployment.yaml
git commit -m "refactor: Helm 健康检查路径适配 /api 前缀"
```

---

### Task 11: 编译验证 — 全量构建

- [ ] **Step 1: 前端构建**

Run: `cd frontend && npm run build`
Expected: 构建成功，无错误

- [ ] **Step 2: 后端构建**

Run: `cd backend && mvn clean package -Dmaven.test.skip=true`
Expected: BUILD SUCCESS

- [ ] **Step 3: 确认最终 commit**

```bash
git log --oneline -10
```
验证所有 commit 都在分支上。
