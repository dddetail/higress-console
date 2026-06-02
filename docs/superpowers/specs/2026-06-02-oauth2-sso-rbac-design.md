# Higress Console OAuth2 SSO 与 RBAC 权限管理 — 产品需求文档

| 字段 | 内容 |
|------|------|
| 文档版本 | v1.0 |
| 创建日期 | 2026-06-02 |
| 状态 | Draft |
| 作者 | — |

---

## 一、背景与动机

Higress Console 当前仅支持单一管理员账号（admin），凭据存储在 Kubernetes Secret 中，通过用户名/密码 + Cookie/Basic Auth 进行认证。这种设计在个人或小团队内部使用时足够，但无法满足以下场景：

1. **团队协作**：多人需要登录同一控制台，各自拥有不同权限
2. **多租户隔离**：作为公共 API 网关平台，不同应用/服务的运维人员只能管理自己名下的资源
3. **统一身份认证**：企业用户希望复用已有的 GitHub/GitLab 等身份源，无需额外管理一套账密

本需求旨在引入 OAuth2 SSO 登录能力和基于 Consumer 的 RBAC 权限体系，将 Higress Console 从单用户管理工具升级为支持多用户、多租户的 API 网关管理平台。

---

## 二、目标用户与角色

### 2.1 平台管理员（Platform Admin）

- 系统初始化时创建的第一个用户，拥有平台级最高权限
- 职责：管理系统全局配置、OAuth2 Provider 配置、用户管理、Consumer 与用户绑定
- 等同于当前的 admin 用户，但登录方式扩展为支持 SSO

### 2.2 应用 Owner

- 归属于某个 Consumer（应用/服务）
- 职责：管理所属应用下的所有资源（路由、域名、API 发布与授权等），管理应用下的成员（邀请/移除/变更角色）
- 一个用户可以是多个不同 Consumer 的 Owner

### 2.3 应用 Manager

- 归属于某个 Consumer（应用/服务）
- 职责：管理所属应用下的资源（路由、域名、API 发布等），**不能**管理成员
- 可以发布和授权 API

### 2.4 应用 Reader

- 归属于某个 Consumer（应用/服务）
- 职责：只读查看所属应用下的资源状态
- 不能进行任何修改操作

### 2.5 角色层级关系

```
平台管理员（Platform Admin）
  └─ 全局权限，跨所有 Consumer

应用 Owner
  └─ Consumer 级别最高权限，含成员管理

应用 Manager
  └─ Consumer 级别资源管理，不含成员管理

应用 Reader
  └─ Consumer 级别只读查看
```

> 一个 SSO 用户可以被分配到多个 Consumer，在每个 Consumer 中拥有独立角色。
> 平台管理员与 Consumer 角色独立，管理员也可以同时归属于某个 Consumer。

---

## 三、功能需求

### 第一期：OAuth2 SSO 登录 + 用户体系

#### 3.1 登录页面改造

**需求描述**：改造现有登录页面，在用户名/密码登录表单之外，增加 SSO 登录入口。

**功能要点**：

- 保留现有的用户名/密码登录表单，功能不变
- 在登录表单下方增加分隔线，分隔线下方展示已配置的 OAuth2 Provider 登录按钮
- 每个 Provider 展示为独立按钮，包含 Provider 图标（如 GitHub logo）和名称
- 未配置任何 OAuth2 Provider 或 SSO 功能未启用时，SSO 登录区域不展示
- 支持国际化（中英文）

**交互流程**：

1. 用户在登录页看到用户名密码表单 + 下方 SSO 登录按钮区域
2. 点击某个 SSO 按钮（如 "Sign in with GitHub"）
3. 浏览器重定向到对应 OAuth2 Provider 的授权页面
4. 用户在 Provider 侧完成授权
5. Provider 回调到 Higress Console 后端
6. 后端完成 token 交换、获取用户信息、匹配本地用户
7. 成功：重定向到控制台首页，种下 Session Cookie
8. 失败：重定向回登录页，显示错误提示

#### 3.2 OAuth2 Provider 配置管理

**需求描述**：平台管理员可以在控制台中配置和管理 OAuth2 Provider。

**功能要点**：

- 提供预设模板（GitHub、GitLab），管理员填写 Client ID 和 Client Secret 即可快速启用
- 支持自定义 OAuth2 Provider，需填写完整配置：
  - Provider 名称和标识
  - Authorization URL
  - Token URL
  - User Info URL
  - Scope（默认 `read:user,user:email`）
  - Client ID
  - Client Secret
- Provider 的启用/禁用开关
- 配置即时生效，无需重启服务
- Client Secret 等敏感信息加密存储，管理界面中脱敏显示

**SSO 功能开关**：

- SSO 功能的启用/禁用在"系统设置 > SSO 配置"页面中控制，无需通过环境变量或重启服务
- 管理员在 SSO 配置页面中看到一个全局开关，关闭后：
  - 登录页不展示 SSO 登录按钮区域
  - 后端 `/oauth2/` 相关端点返回 404 或拒绝服务
  - 已有的 OAuth2 Provider 配置数据保留，不删除
- 启用 SSO 功能需要至少配置一个已启用的 OAuth2 Provider，否则提示管理员先完成配置
- 开关状态变更即时生效

#### 3.3 OAuth2 后端接口

**需求描述**：后端提供 OAuth2 授权流程所需的 API 端点。

**API 列表**：

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| GET | `/oauth2/providers` | 获取已启用的 OAuth2 Provider 列表（前端用于渲染登录按钮） | 免登录 |
| GET | `/oauth2/authorization/{provider}` | 发起 OAuth2 授权，重定向到 Provider 授权页 | 免登录 |
| GET | `/oauth2/callback/{provider}` | OAuth2 回调端点，接收 authorization code，完成 token 交换 | 免登录 |

**处理逻辑**：

1. `/oauth2/authorization/{provider}`：
   - 检查 SSO 功能是否已启用，未启用则拒绝请求
   - 生成随机 `state` 参数，存入缓存（防 CSRF）
   - 构建 Provider 的授权 URL（含 Client ID、Redirect URI、Scope、State）
   - 302 重定向到 Provider 授权页

2. `/oauth2/callback/{provider}`：
   - 验证 `state` 参数
   - 用 authorization code 向 Provider 换取 access_token 和 refresh_token
   - 用 access_token 调用 Provider 的 User Info API 获取用户信息
   - 根据 Provider + Provider 用户 ID 查找本地用户
   - 用户存在：更新用户信息（如头像、显示名称），存储刷新后的 token，创建 Session
   - 用户不存在：创建新用户记录，存储 OAuth2 token，创建 Session（一期默认无 Consumer 绑定）
   - 种下 Session Cookie，302 重定向到前端首页

3. **Token 刷新**：
   - 当 access_token 过期时，使用 refresh_token 向 Provider 自动刷新，更新存储
   - 若 refresh_token 也失效，清除本地 token 记录，要求用户重新登录授权
   - Token 刷新过程对用户透明

#### 3.4 用户管理

**需求描述**：平台管理员可以查看和管理所有通过 SSO 登录的用户。

**数据模型 — User 表**：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT (PK) | 用户 ID |
| username | VARCHAR(64) | 用户名（唯一） |
| display_name | VARCHAR(128) | 显示名称 |
| employee_id | VARCHAR(64) | 工号（可选） |
| type | VARCHAR(16) | 用户类型：`platform_admin` / `consumer_user` |
| status | VARCHAR(16) | 状态：`active` / `disabled` |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

**数据模型 — OAuth2Account 表**：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT (PK) | 记录 ID |
| user_id | BIGINT (FK) | 关联 User.id |
| provider | VARCHAR(32) | Provider 标识，如 `github`、`gitlab` |
| provider_user_id | VARCHAR(128) | Provider 侧的用户 ID |
| provider_username | VARCHAR(64) | Provider 侧的用户名 |
| access_token | VARCHAR(512) | 加密存储的 access_token |
| refresh_token | VARCHAR(512) | 加密存储的 refresh_token |
| token_expires_at | DATETIME | Token 过期时间 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

> 联合唯一索引：(provider, provider_user_id)

**数据模型 — OAuth2Provider 表**：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT (PK) | 记录 ID |
| name | VARCHAR(64) | 显示名称，如 "GitHub" |
| provider_key | VARCHAR(32) | 标识，如 `github`、`gitlab`、`custom_xxx` |
| authorization_url | VARCHAR(512) | 授权端点 URL |
| token_url | VARCHAR(512) | Token 端点 URL |
| user_info_url | VARCHAR(512) | 用户信息端点 URL |
| scope | VARCHAR(128) | OAuth2 Scope |
| client_id | VARCHAR(256) | Client ID |
| client_secret | VARCHAR(512) | 加密存储的 Client Secret |
| icon_url | VARCHAR(512) | Provider 图标 URL（可选） |
| enabled | TINYINT | 是否启用：0/1 |
| is_preset | TINYINT | 是否预设模板：0/1 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

**管理界面功能**：

- 用户列表页：展示所有用户，支持按用户名/类型/状态筛选
- 用户详情页：展示用户基本信息、关联的 OAuth2 账号、所属 Consumer 及角色（二期）
- 禁用/启用用户：管理员可以禁用某个用户，禁用后该用户无法登录
- 删除用户：删除用户记录及其 OAuth2 绑定关系

#### 3.5 Session 机制扩展

**需求描述**：扩展现有 Session 机制，兼容原有密码登录和新增的 OAuth2 登录。

**功能要点**：

- 原有 admin 用户名/密码登录保持不变，Cookie 名称和加密机制不变
- OAuth2 登录成功后复用同一 Session Cookie（`_hi_sess`），但 Token 内容区分来源
- `SessionService.validateSession()` 方法扩展：先尝试原有 Cookie/Basic Auth 验证，再尝试 OAuth2 Session 验证
- `ApiStandardizationAspect` 的拦截逻辑不需要修改，仍通过 `SessionService.validateSession()` 获取当前用户

#### 3.6 数据库引入

**需求描述**：引入 MySQL 作为用户体系和配置数据的存储。

**功能要点**：

- 使用 Spring Data JPA 作为 ORM 框架
- 数据库连接信息通过环境变量配置：
  - `HIGRESS_DB_URL`：JDBC URL
  - `HIGRESS_DB_USERNAME`：用户名
  - `HIGRESS_DB_PASSWORD`：密码
- 应用启动时自动执行 DDL（通过 Flyway 或 Liquibase 迁移脚本）
- 现有的 K8s Secret 存储机制（admin 凭据、配置等）保持不变，新增的数据存入 MySQL
- MySQL 为独立部署，不由 Higress Console Helm Chart 管理
- SSO 功能开关状态存储在数据库中（如存入系统配置表），而非环境变量

---

### 第二期：RBAC 权限 + Consumer 级资源隔离

#### 3.7 Consumer 成员管理

**需求描述**：Consumer（应用/服务）下支持多用户，每个用户拥有 reader/manager/owner 角色。

**数据模型 — ConsumerMember 表**：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT (PK) | 记录 ID |
| consumer_name | VARCHAR(64) | Consumer 名称（关联现有 Consumer） |
| user_id | BIGINT (FK) | 关联 User.id |
| role | VARCHAR(16) | 角色：`owner` / `manager` / `reader` |
| created_at | DATETIME | 加入时间 |
| updated_at | DATETIME | 更新时间 |

> 联合唯一索引：(consumer_name, user_id)

**管理界面功能**：

- 平台管理员可以在 Consumer 详情页中查看和编辑该 Consumer 的成员列表
- 管理员可以将 SSO 用户分配到某个 Consumer，并指定角色
- Consumer 的 Owner 可以管理本 Consumer 的成员（添加/移除/变更角色为 manager 或 reader），但不能修改其他 Owner 的角色
- Consumer 的 Manager 和 Reader 不能管理成员

#### 3.8 角色权限定义

**平台管理员权限**：

| 功能模块 | 权限 |
|----------|------|
| 系统初始化 | 读写 |
| OAuth2 Provider 配置 | 读写 |
| 用户管理 | 读写（含禁用/删除） |
| Consumer 管理 | 读写（含成员分配） |
| 路由管理 | 读写（跨所有 Consumer） |
| 域名管理 | 读写 |
| 服务来源 | 读写 |
| 插件配置 | 读写（全局级） |
| TLS 证书 | 读写 |
| Dashboard | 查看 |
| AI 配置（LLM Provider/AI 路由） | 读写 |
| MCP Server | 读写 |

**Consumer Owner 权限**：

| 功能模块 | 权限 |
|----------|------|
| Consumer 基本信息 | 读写 |
| Consumer 成员管理 | 读写 |
| 路由管理 | 仅管理关联本 Consumer 的路由 |
| API 发布与授权 | 读写 |
| 插件配置 | 读写（Consumer 级） |
| Dashboard | 查看本 Consumer 相关数据 |

**Consumer Manager 权限**：

| 功能模块 | 权限 |
|----------|------|
| Consumer 基本信息 | 只读 |
| Consumer 成员管理 | 无 |
| 路由管理 | 仅管理关联本 Consumer 的路由 |
| API 发布与授权 | 读写 |
| 插件配置 | 读写（Consumer 级） |
| Dashboard | 查看本 Consumer 相关数据 |

**Consumer Reader 权限**：

| 功能模块 | 权限 |
|----------|------|
| Consumer 基本信息 | 只读 |
| Consumer 成员管理 | 无 |
| 路由管理 | 只读（仅本 Consumer 的路由） |
| API 发布与授权 | 只读 |
| 插件配置 | 只读（Consumer 级） |
| Dashboard | 查看本 Consumer 相关数据 |

#### 3.9 资源访问控制

**需求描述**：用户登录后，只能看到和操作被授权的资源。

**功能要点**：

- **前端导航过滤**：根据用户角色动态展示侧边栏菜单，无权限的模块不展示
- **数据范围隔离**：Consumer 用户只能查询到本 Consumer 下的路由、域名、插件等资源
- **操作按钮控制**：只读角色（Reader）不展示"新建"、"编辑"、"删除"等操作按钮
- **后端接口鉴权**：每个 API 请求在后端校验当前用户是否有权限执行该操作，无权限返回 403
- **平台管理员不受限制**：平台管理员可以看到所有 Consumer 的所有资源
- **未分配 Consumer 的用户**：SSO 用户首次登录且未被管理员分配到任何 Consumer 时，进入控制台后展示空白页 + 引导提示文案（如："您尚未被分配到任何应用，请联系平台管理员"）

#### 3.10 初始化流程变更

**需求描述**：系统初始化流程需要适配新的用户体系。

**变更要点**：

- 首次部署的初始化页面：管理员设置密码（保持不变），同时可以选择立即配置 OAuth2 Provider
- 已有部署升级：现有的 admin 用户自动成为平台管理员，密码登录方式保留
- 初始化完成后，管理员可以进入"系统设置 > SSO 配置"页面配置 OAuth2 Provider

---

## 四、非功能需求

### 4.1 安全性

- OAuth2 流程必须使用 `state` 参数防止 CSRF 攻击
- Client Secret 和 OAuth2 Token 必须加密存储（AES-256），不可明文落库
- Session Cookie 保持 `HttpOnly` 属性
- 建议生产环境启用 HTTPS，OAuth2 回调 URL 必须为 HTTPS
- 用户禁用后立即失效其 Session
- 支持 refresh_token 自动刷新 access_token，刷新过程对用户透明

### 4.2 兼容性

- **向后兼容**：原有 admin 用户名/密码登录方式不受影响，已部署实例无需数据迁移即可正常使用
- **K8s Secret 兼容**：admin 凭据仍存储在 K8s Secret 中，不迁移到 MySQL
- **API 兼容**：现有 API 签名和响应格式不变，新增 OAuth2 相关 API 使用独立路径前缀 `/oauth2/`

### 4.3 可观测性

- OAuth2 认证流程的关键步骤记录日志：授权发起、回调成功/失败、用户创建/匹配、Token 刷新
- 用户登录/登出事件记录日志，包含用户 ID、登录方式、来源 IP
- OAuth2 Provider 调用失败时记录详细的错误信息（不含敏感 Token）

### 4.4 性能

- OAuth2 Provider 配置和 SSO 开关状态支持缓存，避免每次授权请求都查数据库
- 用户 Session 验证性能不因新增 OAuth2 分支而显著下降

---

## 五、UI 页面清单

### 一期新增/修改

| 页面 | 类型 | 说明 |
|------|------|------|
| 登录页 | 修改 | 新增 SSO 登录按钮区域 |
| 系统设置 > SSO 配置 | 新增 | SSO 功能全局开关 + OAuth2 Provider 的增删改查管理页 |
| 用户管理列表 | 新增 | 展示所有用户，支持搜索和状态管理 |
| 用户详情页 | 新增 | 展示用户基本信息和 OAuth2 绑定信息 |

### 二期新增/修改

| 页面 | 类型 | 说明 |
|------|------|------|
| Consumer 详情 > 成员管理 | 新增 | Consumer 下的成员列表和角色管理 |
| 全局导航 | 修改 | 根据用户角色动态过滤菜单 |
| 资源列表页（路由/域名等） | 修改 | 根据用户角色控制可见资源和操作按钮 |
| 空状态引导页 | 新增 | 未分配 Consumer 的用户看到的引导提示 |

---

## 六、API 清单

### 一期新增

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/oauth2/providers` | 获取已启用的 OAuth2 Provider 列表（免登录） |
| GET | `/oauth2/authorization/{provider}` | 发起 OAuth2 授权（免登录） |
| GET | `/oauth2/callback/{provider}` | OAuth2 回调处理（免登录） |
| GET | `/v1/users` | 用户列表（管理员） |
| GET | `/v1/users/{id}` | 用户详情（管理员） |
| PUT | `/v1/users/{id}/status` | 启用/禁用用户（管理员） |
| DELETE | `/v1/users/{id}` | 删除用户（管理员） |
| GET | `/v1/oauth2-providers` | OAuth2 Provider 列表（管理员） |
| POST | `/v1/oauth2-providers` | 新增 OAuth2 Provider（管理员） |
| PUT | `/v1/oauth2-providers/{id}` | 更新 OAuth2 Provider（管理员） |
| DELETE | `/v1/oauth2-providers/{id}` | 删除 OAuth2 Provider（管理员） |
| GET | `/v1/system/sso-status` | 获取 SSO 功能开关状态 |
| PUT | `/v1/system/sso-status` | 切换 SSO 功能开关（管理员） |

### 二期新增

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/v1/consumers/{name}/members` | Consumer 成员列表 |
| POST | `/v1/consumers/{name}/members` | 添加成员并分配角色 |
| PUT | `/v1/consumers/{name}/members/{userId}` | 变更成员角色 |
| DELETE | `/v1/consumers/{name}/members/{userId}` | 移除成员 |

---

## 七、分期计划

### 第一期 — OAuth2 SSO + 用户体系

**目标**：用户可以通过 GitHub 等第三方 SSO 登录 Higress Console，管理员可以管理 OAuth2 配置和用户。

**范围**：
- 引入 MySQL（独立部署） + Spring Data JPA
- SSO 功能全局开关（页面配置，即时生效）
- OAuth2 Provider 配置管理（含预设模板）
- 登录页面 SSO 入口
- OAuth2 后端授权流程（含 Token 刷新）
- 用户管理（查看、禁用、删除）
- Session 机制扩展
- SSO 登录的用户一期暂不区分角色，登录后拥有与当前 admin 相同的全部权限

**不含**：
- RBAC 角色权限控制
- Consumer 级资源隔离
- Consumer 成员管理

### 第二期 — RBAC + 资源隔离

**目标**：实现基于 Consumer 的多租户权限隔离，不同角色的用户只能访问被授权的资源和操作。

**范围**：
- ConsumerMember 数据模型
- Consumer 成员管理界面
- 角色权限定义（owner/manager/reader）
- 前端导航和菜单动态过滤
- 后端接口权限校验
- 资源数据范围隔离
- 未分配用户的空状态引导页
- 初始化流程适配

---

## 八、约束与假设

1. **单一 Provider 部署**：实际生产环境中通常只配置一套 OAuth2 Provider，系统设计支持多 Provider 但不以此为主要场景
2. **MySQL 独立部署**：MySQL 不由 Higress Console 的 Helm Chart 管理，需用户自行部署和维护
3. **每个用户绑定一个 OAuth2 Provider**：系统支持多 Provider 配置，但单个用户通常只通过一个 Provider 登录
4. **SSO 开关由页面控制**：SSO 功能的启用/禁用在管理页面操作，配置存入数据库，无需重启服务或修改环境变量
