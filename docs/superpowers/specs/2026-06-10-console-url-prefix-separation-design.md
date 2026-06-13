# 前后端 URL 路径空间分离设计

**日期**: 2026-06-10
**状态**: 已批准
**分支**: refactor/consumer-is-group → 后续迁移到新分支

## 1. 问题背景

Higress Console 的前后端打包在同一个 Spring Boot JAR 中部署。当前前端 SPA 路由和后端 API 控制器共享同一个路径空间，导致以下冲突：

| 前端路由 | 后端控制器 | 冲突情况 |
|---|---|---|
| `/dashboard` | `DashboardController` | 路径完全重叠 |
| `/system` | `SystemController` | 路径完全重叠 |
| `/user/list` | `UserController` | 前缀重叠 |
| `/user/changePassword` | `UserController` | 前缀重叠 |

当前通过 `SpaRoutingFilter`（基于 `Accept: text/html` 头检测）和 `WebMvcInitializer`（静态资源 fallback）两层机制解决这个问题，但存在以下缺陷：

- 完全依赖 `Accept` 头区分前后端请求，不够可靠
- OAuth2 回调路径 `/oauth2/callback/` 在特定情况下可能被错误拦截
- 两层防护逻辑复杂，维护成本高
- `WebMvcInitializer.API_PATH_PREFIXES` 中缺少 `/aiproxy` 前缀（已有 bug）

## 2. 设计目标

1. 前端页面路由和后端 API 路径完全分离，消除所有冲突
2. 彻底删除 `SpaRoutingFilter`，简化 `WebMvcInitializer`
3. 修复 OAuth2 回调重定向问题
4. 不需要向后兼容旧 URL

## 3. 方案概述

| 路径前缀 | 用途 | 示例 |
|---|---|---|
| `/console/*` | 前端 SPA 页面 | `/console/dashboard`、`/console/user/list` |
| `/api/*` | 后端 API | `/api/v1/routes`、`/api/oauth2/callback` |
| `/assets/*` 等 | 静态资源 | 不受影响 |
| `/` | 重定向到 `/console/` | 访问根路径自动跳转 |

## 4. 前端改造

### 4.1 ICE.js 路由配置 (`ice.config.mts`)

添加 `basename` 配置，使所有前端路由自动带上 `/console` 前缀：

```ts
// ice.config.mts 中 defineConfig 的 router 配置
router: {
  basename: '/console',
},
```

前端路由定义文件 `src/pages/_defaultProps.tsx` 中的路径（如 `/dashboard`、`/user/list`）**不需要修改**，ICE.js 的 basename 机制会自动处理。

### 4.2 开发代理配置 (`ice.config.mts`)

由于后端 API 路径已经自带 `/api` 前缀，proxy 不再需要 pathRewrite：

```ts
proxy: {
  "/api": {
    target: "http://localhost:8080",
    changeOrigin: true,
    // 不再需要 pathRewrite，后端直接接收 /api/xxx
  },
},
```

### 4.3 API 请求 baseURL (`src/services/request.tsx`)

生产模式 baseURL 从 `""` 改为 `"/api"`：

```ts
const request = axios.create({
  timeout: 5 * 1000,
  baseURL: "/api", // 统一使用 /api 前缀，dev 和 prod 一致
});
```

开发模式下 `/api` 请求通过 proxy 转发到后端；生产模式下直接请求同源 `/api` 路径。两种环境逻辑统一。

### 4.4 OAuth2 SSO 登录跳转 (`src/pages/login/index.tsx`)

`handleSsoLogin` 不再需要区分 dev/prod：

```ts
function handleSsoLogin(providerKey: string) {
  window.location.href = `/api/oauth2/authorization/${providerKey}`;
}
```

### 4.5 401 重定向逻辑 (`src/services/request.tsx`)

响应拦截器中 401 跳转路径从 `/login` 改为 `/console/login`：

```ts
window.location.href = `/console/login?redirect=${encodeURIComponent(redirect)}`;
```

### 4.6 其他硬编码路径排查

需要检查并更新以下位置的硬编码路径：
- 所有 `navigate('/xxx')` 调用 — ICE.js basename 会自动处理相对路径，但绝对路径可能需要调整
- 所有 `window.location.href = '/xxx'` 调用
- 国际化文件中的 URL 引用（如果有）

## 5. 后端改造

### 5.1 API 路径自动加前缀 (`WebMvcInitializer`)

使用 `WebMvcConfigurer.configurePathMatch()` 给所有控制器自动加上 `/api` 前缀。静态资源不受影响：

```java
@Override
public void configurePathMatch(PathMatchConfigurer configurer) {
    configurer.addPathPrefix("/api", c ->
        c.isAnnotationPresent(RestController.class) || c.isAnnotationPresent(Controller.class)
    );
}
```

这样所有控制器的 `@RequestMapping` 无需修改，运行时路径自动变为 `/api/v1/xxx`、`/api/oauth2/xxx` 等。

### 5.2 删除 SpaRoutingFilter

完全删除 `backend/console/src/main/java/com/alibaba/higress/console/filter/SpaRoutingFilter.java`。

前后端路径空间完全分离后，不再需要通过 `Accept` 头检测来区分浏览器导航请求和 API 请求。

### 5.3 简化 WebMvcInitializer

`WebMvcInitializer` 从复杂的 API 路径排除逻辑简化为：

```java
@Override
public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry.addResourceHandler("/*")
        .addResourceLocations("classpath:/static/")
        .setCacheControl(CacheControl.maxAge(Duration.ZERO).mustRevalidate())
        .setUseLastModified(true)
        .resourceChain(true)
        .addResolver(new PathResourceResolver() {
            @Override
            protected Resource resolveResourceInternal(...) {
                Resource resource = super.resolveResourceInternal(...);
                if (resource == null && requestPath.startsWith("console")) {
                    // 前端 SPA 路由 fallback 到 index.html
                    resource = super.resolveResourceInternal(request, HOMEPAGE_PATH, locations, chain);
                }
                return resource;
            }
        });
}
```

核心变化：
- 删除 `API_PATH_PREFIXES` 列表（不再需要）
- 只对 `/console` 开头的路径做 SPA fallback
- `/api` 路径不做 fallback，由 Spring MVC 正常处理（404 就是 404）

### 5.4 根路径重定向

添加根路径 `/` 到 `/console/` 的重定向。可以在 `WebMvcInitializer` 中实现：

```java
@Override
public void addViewControllers(ViewControllerRegistry registry) {
    registry.addRedirectViewController("/", "/console/");
}
```

### 5.5 OAuth2 重定向 URL 更新 (`Oauth2Controller`)

| 场景 | 改造前 | 改造后 |
|---|---|---|
| OAuth2 回调 URL | `/oauth2/callback/{provider}` | `/api/oauth2/callback/{provider}` |
| 登录成功重定向 | `/` | `/console/` |
| 登录失败重定向 | `/login?oauth_error=xxx` | `/console/login?oauth_error=xxx` |

`buildRedirectUri` 方法中拼接的回调路径需更新为 `/api/oauth2/callback/{providerKey}`。

`sendSuccessRedirect` 中：
- 如果配置了 `redirect-base-url`，重定向到 `{redirect-base-url}/console/`
- 否则重定向到 `/console/`

`redirectToLoginWithError` 中：
- 如果配置了 `redirect-base-url`，重定向到 `{redirect-base-url}/console/login?oauth_error=xxx`
- 否则重定向到 `/console/login?oauth_error=xxx`

### 5.6 SpringDoc 配置更新 (`application.properties`)

```properties
springdoc.pathsToMatch=/api/**
```

## 6. 部署影响

### 6.1 K8s 健康检查

健康检查端点从 `/healthz/ready` 变为 `/api/healthz/ready`（HealthzController 类级 `/healthz` + 方法级 `/ready`，configurePathMatch 加 `/api` 前缀）。需要更新 Helm chart 中的探针配置：

```yaml
readinessProbe:
  httpGet:
    path: /api/healthz/ready
```

> 注意：`/api/healthz`（不带 `/ready`）会 404，因为 HealthzController 没有 `/healthz` 根级 GET 映射。

### 6.2 OAuth2 提供者配置

第三方 OAuth2 提供者（GitHub、Google 等）中配置的回调 URL 需要更新：
- 从 `https://domain/oauth2/callback/{provider}` 改为 `https://domain/api/oauth2/callback/{provider}`

这是部署配置变更，不涉及代码修改。

### 6.3 Session Cookie

需要确保 session cookie 的 path 为 `/`，这样 `/api` 和 `/console` 都能共享认证状态。Spring Boot 默认 cookie path 为 context-path（即 `/`），不受影响。

### 6.4 CORS（如有）

如果配置了 CORS，需要更新允许的路径。

## 7. 不受影响的部分

- **控制器 Java 代码**：`configurePathMatch` 自动加前缀，零改动
- **前端路由定义**：`basename` 自动处理，`_defaultProps.tsx` 路径不变
- **SDK 模块**：纯业务逻辑，不涉及 HTTP 路径
- **数据库 schema**：无 Flyway 迁移
- **前端页面组件**：页面逻辑不变，只涉及路径配置
- **权限/RBAC 体系**：AOP 切面基于注解，不受路径变更影响

## 8. 改动文件清单

### 前端
| 文件 | 改动 |
|---|---|
| `frontend/ice.config.mts` | 添加 `basename: '/console'`，调整 proxy 配置 |
| `frontend/src/services/request.tsx` | baseURL 改为 `"/api"`，401 跳转改为 `/console/login` |
| `frontend/src/pages/login/index.tsx` | SSO 跳转路径统一 |
| 其他含硬编码路径的文件 | 排查并更新 |

### 后端
| 文件 | 改动 |
|---|---|
| `WebMvcInitializer.java` | 添加 `configurePathMatch()`，简化 fallback 逻辑，添加根路径重定向 |
| `SpaRoutingFilter.java` | 删除 |
| `Oauth2Controller.java` | 回调路径、成功/失败重定向 URL 更新 |
| `application.properties` | springdoc 路径更新 |

### 部署
| 文件 | 改动 |
|---|---|
| `helm/values.yaml` | 健康检查探针路径更新 |
