# OAuth2 SSO + 用户体系 一期实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 Higress Console 引入 OAuth2 SSO 登录能力和用户管理体系，使用户可以通过 GitHub 等第三方服务单点登录。

**Architecture:** 在现有 SessionService + AOP 认证体系上扩展，后端新增 OAuth2 客户端模块处理授权流程，引入 MySQL + Spring Data JPA 存储用户和配置数据。前端登录页新增 SSO 登录入口，新增 SSO 配置管理和用户管理页面。

**Tech Stack:** Spring Boot 2.7 / Spring Data JPA / MySQL / Flyway / Java 8 / React 18 / Ant Design Pro / ice.js

**PRD:** `docs/superpowers/specs/2026-06-02-oauth2-sso-rbac-design.md`

---

## File Structure

### 后端新增文件

```
backend/console/src/main/java/com/alibaba/higress/console/
├── config/
│   └── DataSourceConfig.java              # JPA 数据源配置
├── controller/
│   ├── OAuth2Controller.java              # OAuth2 授权/回调端点
│   ├── OAuth2ProviderController.java      # Provider CRUD 管理
│   └── UserController.java               # 修改：扩展用户管理接口
├── controller/dto/
│   ├── OAuth2ProviderRequest.java         # Provider 创建/更新请求
│   ├── UserStatusRequest.java             # 用户状态变更请求
│   └── SsoStatusResponse.java             # SSO 开关状态响应
├── model/
│   ├── User.java                          # 修改：增加 type/status/employeeId 字段
│   └── OAuth2State.java                   # OAuth2 state 参数缓存对象
├── repository/
│   ├── UserRepository.java                # 用户 JPA Repository
│   └── OAuth2ProviderRepository.java      # Provider JPA Repository
├── repository/entity/
│   ├── UserEntity.java                    # 用户数据库实体
│   └── OAuth2ProviderEntity.java          # Provider 数据库实体
├── service/
│   ├── SessionService.java               # 修改：扩展接口
│   ├── SessionServiceImpl.java           # 修改：扩展实现
│   ├── OAuth2Service.java                 # OAuth2 核心服务接口
│   ├── OAuth2ServiceImpl.java             # OAuth2 核心服务实现
│   ├── UserService.java                   # 用户管理服务
│   ├── UserServiceImpl.java               # 用户管理服务实现
│   └── SsoConfigService.java              # SSO 开关与配置服务
└── service/oauth2/
    ├── OAuth2Client.java                  # OAuth2 HTTP 客户端（token 交换、用户信息获取）
    ├── OAuth2StateService.java            # State 参数生成与验证
    ├── UserInfoMapper.java                # Provider 用户信息 → 本地用户映射
    └── PresetProviders.java               # 预设 Provider 模板（GitHub/GitLab）

backend/console/src/main/resources/
└── db/migration/
    ├── V1__create_user_table.sql
    └── V2__create_oauth2_provider_table.sql

backend/console/pom.xml                    # 修改：添加 JPA/MySQL/Flyway 依赖
```

### 前端新增/修改文件

```
frontend/src/
├── interfaces/
│   ├── user.ts                            # 修改：扩展 UserInfo/增加 OAuth2Provider 接口
│   └── config.ts                          # 修改：增加 SSO 相关常量
├── services/
│   ├── user.ts                            # 修改：增加用户管理 API
│   └── oauth2.ts                          # 新增：SSO 配置相关 API
├── pages/
│   ├── login/index.tsx                    # 修改：添加 SSO 登录按钮区域
│   ├── system/index.tsx                   # 修改：增加 SSO 配置 tab
│   └── user/
│       ├── list.tsx                       # 新增：用户管理列表页
│       └── detail.tsx                     # 新增：用户详情页
└── locales/
    ├── zh-CN/translation.json             # 修改：添加 SSO/用户管理相关文案
    └── en-US/translation.json             # 修改：添加 SSO/用户管理相关文案
```

---

## Task 1: 引入 MySQL + JPA + Flyway 依赖

**Files:**
- Modify: `backend/console/pom.xml`
- Modify: `backend/console/src/main/resources/application.properties`

- [ ] **Step 1: 在 console/pom.xml 中添加 JPA、MySQL 驱动、Flyway 依赖**

在 `backend/console/pom.xml` 的 `<dependencies>` 中添加：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
    <groupId>mysql</groupId>
    <artifactId>mysql-connector-java</artifactId>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-mysql</artifactId>
</dependency>
```

- [ ] **Step 2: 在 application.properties 中添加数据源和 JPA 配置**

在 `backend/console/src/main/resources/application.properties` 末尾添加：

```properties
# DataSource - configured via environment variables
spring.datasource.url=${HIGRESS_DB_URL:}
spring.datasource.username=${HIGRESS_DB_USERNAME:}
spring.datasource.password=${HIGRESS_DB_PASSWORD:}
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

# JPA
spring.jpa.hibernate.ddl-auto=none
spring.jpa.show-sql=false
spring.jpa.open-in-view=false

# Flyway
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
spring.flyway.baseline-on-migrate=true
```

- [ ] **Step 3: 创建 DataSourceConfig.java**

当数据库未配置时（`HIGRESS_DB_URL` 为空），应用应能正常启动，只是 JPA 相关功能不可用。创建条件化配置：

```java
/*
 * Copyright (c) 2022-2026 Alibaba Group Holding Ltd.
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
package com.alibaba.higress.console.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@ConditionalOnProperty(name = "spring.datasource.url", matchIfMissing = false)
@EnableJpaRepositories(basePackages = "com.alibaba.higress.console.repository")
@EnableTransactionManagement
public class DataSourceConfig {
}
```

- [ ] **Step 4: 验证编译通过**

Run: `cd backend && mvn compile -pl console -am -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add backend/console/pom.xml backend/console/src/main/resources/application.properties backend/console/src/main/java/com/alibaba/higress/console/config/DataSourceConfig.java
git commit -m "feat: add MySQL, JPA, and Flyway dependencies for OAuth2 SSO"
```

---

## Task 2: 创建数据库迁移脚本（User + OAuth2Provider 表）

**Files:**
- Create: `backend/console/src/main/resources/db/migration/V1__create_user_table.sql`
- Create: `backend/console/src/main/resources/db/migration/V2__create_oauth2_provider_table.sql`

- [ ] **Step 1: 创建 User 表迁移脚本**

```sql
-- V1__create_user_table.sql
CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `username` VARCHAR(64) NOT NULL,
    `display_name` VARCHAR(128) NOT NULL,
    `employee_id` VARCHAR(64) DEFAULT NULL,
    `type` VARCHAR(16) NOT NULL DEFAULT 'consumer_user',
    `status` VARCHAR(16) NOT NULL DEFAULT 'active',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

- [ ] **Step 2: 创建 OAuth2Provider 表迁移脚本**

```sql
-- V2__create_oauth2_provider_table.sql
CREATE TABLE IF NOT EXISTS `oauth2_provider` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(64) NOT NULL,
    `provider_key` VARCHAR(32) NOT NULL,
    `authorization_url` VARCHAR(512) NOT NULL,
    `token_url` VARCHAR(512) NOT NULL,
    `user_info_url` VARCHAR(512) NOT NULL,
    `scope` VARCHAR(128) DEFAULT NULL,
    `client_id` VARCHAR(256) NOT NULL,
    `client_secret` VARCHAR(512) NOT NULL,
    `icon_url` VARCHAR(512) DEFAULT NULL,
    `enabled` TINYINT NOT NULL DEFAULT 1,
    `is_preset` TINYINT NOT NULL DEFAULT 0,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_provider_key` (`provider_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

- [ ] **Step 3: Commit**

```bash
git add backend/console/src/main/resources/db/migration/
git commit -m "feat: add Flyway migration scripts for user and oauth2_provider tables"
```

---

## Task 3: 创建 JPA 实体和 Repository

**Files:**
- Create: `backend/console/src/main/java/com/alibaba/higress/console/repository/entity/UserEntity.java`
- Create: `backend/console/src/main/java/com/alibaba/higress/console/repository/entity/OAuth2ProviderEntity.java`
- Create: `backend/console/src/main/java/com/alibaba/higress/console/repository/UserRepository.java`
- Create: `backend/console/src/main/java/com/alibaba/higress/console/repository/OAuth2ProviderRepository.java`

- [ ] **Step 1: 创建 UserEntity**

```java
/*
 * Copyright (c) 2022-2026 Alibaba Group Holding Ltd.
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
package com.alibaba.higress.console.repository.entity;

import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "`user`")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(nullable = false, length = 128)
    private String displayName;

    @Column(length = 64)
    private String employeeId;

    @Column(nullable = false, length = 16)
    private String type;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 2: 创建 OAuth2ProviderEntity**

```java
/*
 * Copyright (c) 2022-2026 Alibaba Group Holding Ltd.
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
package com.alibaba.higress.console.repository.entity;

import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "oauth2_provider")
public class OAuth2ProviderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(nullable = false, unique = true, length = 32)
    private String providerKey;

    @Column(nullable = false, length = 512)
    private String authorizationUrl;

    @Column(nullable = false, length = 512)
    private String tokenUrl;

    @Column(nullable = false, length = 512)
    private String userInfoUrl;

    @Column(length = 128)
    private String scope;

    @Column(nullable = false, length = 256)
    private String clientId;

    @Column(nullable = false, length = 512)
    private String clientSecret;

    @Column(length = 512)
    private String iconUrl;

    @Column(nullable = false)
    private Boolean enabled;

    @Column(nullable = false)
    private Boolean isPreset;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 3: 创建 UserRepository**

```java
/*
 * Copyright (c) 2022-2026 Alibaba Group Holding Ltd.
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
package com.alibaba.higress.console.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.alibaba.higress.console.repository.entity.UserEntity;

public interface UserRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByUsername(String username);

    boolean existsByUsername(String username);
}
```

- [ ] **Step 4: 创建 OAuth2ProviderRepository**

```java
/*
 * Copyright (c) 2022-2026 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law, agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.alibaba.higress.console.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.alibaba.higress.console.repository.entity.OAuth2ProviderEntity;

public interface OAuth2ProviderRepository extends JpaRepository<OAuth2ProviderEntity, Long> {

    Optional<OAuth2ProviderEntity> findByProviderKey(String providerKey);

    List<OAuth2ProviderEntity> findByEnabledTrue();

    boolean existsByProviderKey(String providerKey);
}
```

- [ ] **Step 5: 验证编译通过**

Run: `cd backend && mvn compile -pl console -am -q`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add backend/console/src/main/java/com/alibaba/higress/console/repository/
git commit -m "feat: add JPA entities and repositories for User and OAuth2Provider"
```

---

## Task 4: 修改 User 模型，扩展 SessionService 接口

**Files:**
- Modify: `backend/console/src/main/java/com/alibaba/higress/console/model/User.java`
- Modify: `backend/console/src/main/java/com/alibaba/higress/console/service/SessionService.java`

- [ ] **Step 1: 扩展 User 模型**

在 `User.java` 中增加 `type`、`status`、`employeeId` 字段，保持 `name`、`password`、`displayName`、`avatarUrl` 不变（`avatarUrl` 保留以兼容现有逻辑，PRD 中"去掉头像字段"仅针对数据库 User 表）：

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
package com.alibaba.higress.console.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    private String name;
    private String password;
    private String displayName;
    private String avatarUrl;
    private String employeeId;
    private String type;
    private String status;
}
```

- [ ] **Step 2: 扩展 SessionService 接口**

在 `SessionService.java` 中增加 OAuth2 登录相关方法：

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
package com.alibaba.higress.console.service;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.alibaba.higress.console.model.User;

/**
 * @author CH3CHO
 */
public interface SessionService {

    boolean isAdminInitialized();

    void initializeAdmin(User user);

    User login(String username, String password);

    void saveSession(HttpServletResponse response, User user, boolean persistent);

    void clearSession(HttpServletResponse response);

    User validateSession(HttpServletRequest request);

    void changePassword(String username, String oldPassword, String newPassword);

    /**
     * Save session for an OAuth2-authenticated user.
     */
    void saveOAuth2Session(HttpServletResponse response, User user);

    /**
     * Validate an OAuth2 session from the request cookie.
     * Returns null if the cookie does not represent an OAuth2 session.
     */
    User validateOAuth2Session(HttpServletRequest request);
}
```

- [ ] **Step 3: 在 SessionServiceImpl 中添加 OAuth2 Session 存根方法**

在 `SessionServiceImpl.java` 中添加两个新方法的实现。OAuth2 Session 使用与现有 Cookie 相同的名称 `_hi_sess`，但在 Token 内容中加入前缀 `oauth2:` 来区分来源：

```java
// Add to SessionServiceImpl.java

@Override
public void saveOAuth2Session(HttpServletResponse response, User user) {
    Cookie cookie = buildEmptyCookie();
    cookie.setValue(generateOAuth2Token(user));
    cookie.setMaxAge(cookieMaxAge);
    response.addCookie(cookie);
}

@Override
public User validateOAuth2Session(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null || cookies.length == 0) {
        return null;
    }
    String token = Arrays.stream(cookies).filter(c -> cookieName.equals(c.getName())).map(Cookie::getValue)
        .findFirst().orElse(null);
    if (Strings.isNullOrEmpty(token)) {
        return null;
    }
    try {
        String rawToken = AesUtil.decrypt(getAdminConfig().getEncryptKey(), getAdminConfig().getEncryptIv(), token);
        if (!rawToken.startsWith("oauth2:")) {
            return null;
        }
        // oauth2:username:timestamp
        String[] segments = rawToken.split(TOKEN_PART_SEPARATOR);
        if (segments.length < 3) {
            return null;
        }
        String username = segments[1];
        // Look up user from UserService/DB would be done in OAuth2Service
        // For now return a basic User object
        return User.builder().name(username).type("consumer_user").build();
    } catch (GeneralSecurityException e) {
        log.warn("Error occurs when decrypting OAuth2 token: " + token, e);
        return null;
    }
}

private String generateOAuth2Token(User user) {
    AdminConfig config = getAdminConfig();
    String rawToken = "oauth2:" + user.getName() + TOKEN_PART_SEPARATOR + String.valueOf(System.currentTimeMillis());
    try {
        return AesUtil.encrypt(config.getEncryptKey(), config.getEncryptIv(), rawToken);
    } catch (GeneralSecurityException e) {
        throw new BusinessException("Error occurs when generating OAuth2 token for user " + user.getName(), e);
    }
}
```

同时修改 `validateSession` 方法，加入 OAuth2 分支：

```java
@Override
public User validateSession(HttpServletRequest request) {
    User user = tryExtractUserFromCookie(request);
    if (user != null) {
        return user;
    }
    user = tryExtractUserFromAuthHeader(request);
    if (user != null) {
        return user;
    }
    return validateOAuth2Session(request);
}
```

- [ ] **Step 4: 验证编译通过**

Run: `cd backend && mvn compile -pl console -am -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add backend/console/src/main/java/com/alibaba/higress/console/model/User.java \
        backend/console/src/main/java/com/alibaba/higress/console/service/SessionService.java \
        backend/console/src/main/java/com/alibaba/higress/console/service/SessionServiceImpl.java
git commit -m "feat: extend User model and SessionService for OAuth2 authentication"
```

---

## Task 5: 实现 OAuth2 核心服务

**Files:**
- Create: `backend/console/src/main/java/com/alibaba/higress/console/service/oauth2/OAuth2Client.java`
- Create: `backend/console/src/main/java/com/alibaba/higress/console/service/oauth2/OAuth2StateService.java`
- Create: `backend/console/src/main/java/com/alibaba/higress/console/service/oauth2/UserInfoMapper.java`
- Create: `backend/console/src/main/java/com/alibaba/higress/console/service/oauth2/PresetProviders.java`
- Create: `backend/console/src/main/java/com/alibaba/higress/console/service/OAuth2Service.java`
- Create: `backend/console/src/main/java/com/alibaba/higress/console/service/OAuth2ServiceImpl.java`

- [ ] **Step 1: 创建 PresetProviders — 预设模板定义**

```java
/*
 * Copyright (c) 2022-2026 Alibaba Group Holding Ltd.
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
package com.alibaba.higress.console.service.oauth2;

import java.util.HashMap;
import java.util.Map;

import com.alibaba.higress.console.repository.entity.OAuth2ProviderEntity;

public final class PresetProviders {

    private static final Map<String, OAuth2ProviderEntity> PRESETS = new HashMap<>();

    static {
        OAuth2ProviderEntity github = new OAuth2ProviderEntity();
        github.setName("GitHub");
        github.setProviderKey("github");
        github.setAuthorizationUrl("https://github.com/login/oauth/authorize");
        github.setTokenUrl("https://github.com/login/oauth/access_token");
        github.setUserInfoUrl("https://api.github.com/user");
        github.setScope("read:user,user:email");
        github.setIconUrl(
            "https://github.githubassets.com/favicons/favicon-dark.svg");
        github.setIsPreset(true);
        PRESETS.put("github", github);

        OAuth2ProviderEntity gitlab = new OAuth2ProviderEntity();
        gitlab.setName("GitLab");
        gitlab.setProviderKey("gitlab");
        gitlab.setAuthorizationUrl("https://gitlab.com/oauth/authorize");
        gitlab.setTokenUrl("https://gitlab.com/oauth/token");
        gitlab.setUserInfoUrl("https://gitlab.com/api/v4/user");
        gitlab.setScope("read_user");
        gitlab.setIconUrl("https://gitlab.com/favicon.ico");
        gitlab.setIsPreset(true);
        PRESETS.put("gitlab", gitlab);
    }

    public static OAuth2ProviderEntity getPreset(String providerKey) {
        return PRESETS.get(providerKey);
    }

    public static Map<String, OAuth2ProviderEntity> allPresets() {
        return new HashMap<>(PRESETS);
    }

    private PresetProviders() {}
}
```

- [ ] **Step 2: 创建 OAuth2StateService — State 参数管理**

```java
/*
 * Copyright (c) 2022-2026 Alibaba Group Holding Ltd.
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
package com.alibaba.higress.console.service.oauth2;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class OAuth2StateService {

    private static final long STATE_TTL_MINUTES = 10;

    private final ConcurrentHashMap<String, Long> stateStore = new ConcurrentHashMap<>();

    private final ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor();

    public OAuth2StateService() {
        cleaner.scheduleAtFixedRate(this::cleanExpired, 1, 1, TimeUnit.MINUTES);
    }

    public String generateState(String providerKey) {
        String state = UUID.randomUUID().toString().replace("-", "");
        stateStore.put(state, System.currentTimeMillis());
        return state;
    }

    public boolean validateAndConsumeState(String state) {
        if (state == null || state.isEmpty()) {
            return false;
        }
        Long timestamp = stateStore.remove(state);
        if (timestamp == null) {
            return false;
        }
        return System.currentTimeMillis() - timestamp < STATE_TTL_MINUTES * 60 * 1000;
    }

    private void cleanExpired() {
        long now = System.currentTimeMillis();
        stateStore.entrySet().removeIf(e -> now - e.getValue() > STATE_TTL_MINUTES * 60 * 1000);
    }
}
```

- [ ] **Step 3: 创建 OAuth2Client — HTTP 客户端**

```java
/*
 * Copyright (c) 2022-2026 Alibaba Group Holding Ltd.
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
package com.alibaba.higress.console.service.oauth2;

import java.io.IOException;
import java.util.Map;

import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.higress.sdk.exception.BusinessException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class OAuth2Client {

    public TokenResponse exchangeToken(String tokenUrl, String clientId, String clientSecret,
        String code, String redirectUri) {
        JSONObject body = new JSONObject();
        body.put("client_id", clientId);
        body.put("client_secret", clientSecret);
        body.put("code", code);
        body.put("redirect_uri", redirectUri);
        body.put("grant_type", "authorization_code");

        HttpPost post = new HttpPost(tokenUrl);
        post.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        post.setHeader(HttpHeaders.ACCEPT, "application/json");
        post.setEntity(new StringEntity(body.toJSONString(), "UTF-8"));

        try (CloseableHttpClient httpClient = HttpClients.createDefault();
             CloseableHttpResponse response = httpClient.execute(post)) {
            String responseBody = EntityUtils.toString(response.getEntity());
            if (response.getStatusLine().getStatusCode() != 200) {
                throw new BusinessException("Token exchange failed: " + responseBody);
            }
            JSONObject json = JSON.parseObject(responseBody);
            return new TokenResponse(
                json.getString("access_token"),
                json.getString("refresh_token"),
                json.getInteger("expires_in")
            );
        } catch (IOException e) {
            throw new BusinessException("Failed to exchange token", e);
        }
    }

    public Map<String, Object> fetchUserInfo(String userInfoUrl, String accessToken) {
        HttpGet get = new HttpGet(userInfoUrl);
        get.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        get.setHeader(HttpHeaders.ACCEPT, "application/json");

        try (CloseableHttpClient httpClient = HttpClients.createDefault();
             CloseableHttpResponse response = httpClient.execute(get)) {
            String responseBody = EntityUtils.toString(response.getEntity());
            if (response.getStatusLine().getStatusCode() != 200) {
                throw new BusinessException("Failed to fetch user info: " + responseBody);
            }
            return JSON.parseObject(responseBody, Map.class);
        } catch (IOException e) {
            throw new BusinessException("Failed to fetch user info", e);
        }
    }

    public static class TokenResponse {
        public final String accessToken;
        public final String refreshToken;
        public final Integer expiresIn;

        public TokenResponse(String accessToken, String refreshToken, Integer expiresIn) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.expiresIn = expiresIn;
        }
    }
}
```

- [ ] **Step 4: 创建 UserInfoMapper — Provider 用户信息映射**

```java
/*
 * Copyright (c) 2022-2026 Alibaba Group Holding Ltd.
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
package com.alibaba.higress.console.service.oauth2;

import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class UserInfoMapper {

    /**
     * Maps provider-specific user info response to a normalized structure.
     * Returns an array: [providerUserId, providerUsername, displayName, email]
     */
    public String[] map(String providerKey, Map<String, Object> userInfo) {
        switch (providerKey) {
            case "github":
                return mapGithub(userInfo);
            case "gitlab":
                return mapGitlab(userInfo);
            default:
                return mapGeneric(userInfo);
        }
    }

    private String[] mapGithub(Map<String, Object> info) {
        String id = String.valueOf(info.get("id"));
        String login = (String) info.get("login");
        String name = (String) info.get("name");
        return new String[]{id, login, name != null ? name : login, null};
    }

    private String[] mapGitlab(Map<String, Object> info) {
        String id = String.valueOf(info.get("id"));
        String username = (String) info.get("username");
        String name = (String) info.get("name");
        return new String[]{id, username, name != null ? name : username, null};
    }

    private String[] mapGeneric(Map<String, Object> info) {
        Object id = info.get("id");
        if (id == null) {
            id = info.get("sub");
        }
        String userId = id != null ? String.valueOf(id) : "";
        String username = (String) info.getOrDefault("username", userId);
        String name = (String) info.getOrDefault("name", username);
        return new String[]{userId, username, name, null};
    }
}
```

- [ ] **Step 5: 创建 OAuth2Service 接口和实现**

`OAuth2Service.java`:
```java
/*
 * Copyright (c) 2022-2026 Alibaba Group Holding Ltd.
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
package com.alibaba.higress.console.service;

import java.util.List;

import com.alibaba.higress.console.repository.entity.OAuth2ProviderEntity;
import com.alibaba.higress.console.model.User;

public interface OAuth2Service {

    /**
     * Build the authorization URL to redirect the user to the OAuth2 provider.
     */
    String buildAuthorizationUrl(String providerKey, String redirectUri);

    /**
     * Handle the OAuth2 callback: exchange code for token, fetch user info,
     * create or update local user, and return the authenticated user.
     */
    User handleCallback(String providerKey, String code, String state, String redirectUri);

    /**
     * Get all enabled providers (for login page rendering).
     */
    List<OAuth2ProviderEntity> getEnabledProviders();
}
```

`OAuth2ServiceImpl.java`:
```java
/*
 * Copyright (c) 2022-2026 Alibaba Group Holding Ltd.
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
package com.alibaba.higress.console.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.alibaba.higress.console.model.User;
import com.alibaba.higress.console.repository.OAuth2ProviderRepository;
import com.alibaba.higress.console.repository.UserRepository;
import com.alibaba.higress.console.repository.entity.OAuth2ProviderEntity;
import com.alibaba.higress.console.repository.entity.UserEntity;
import com.alibaba.higress.console.service.oauth2.OAuth2Client;
import com.alibaba.higress.console.service.oauth2.OAuth2StateService;
import com.alibaba.higress.console.service.oauth2.UserInfoMapper;
import com.alibaba.higress.sdk.exception.BusinessException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class OAuth2ServiceImpl implements OAuth2Service {

    @Resource
    private OAuth2ProviderRepository providerRepository;

    @Resource
    private UserRepository userRepository;

    @Resource
    private OAuth2Client oauth2Client;

    @Resource
    private OAuth2StateService stateService;

    @Resource
    private UserInfoMapper userInfoMapper;

    @Resource
    private SessionService sessionService;

    @Override
    public String buildAuthorizationUrl(String providerKey, String redirectUri) {
        OAuth2ProviderEntity provider = providerRepository.findByProviderKey(providerKey)
            .orElseThrow(() -> new BusinessException("OAuth2 provider not found: " + providerKey));

        if (!provider.getEnabled()) {
            throw new BusinessException("OAuth2 provider is disabled: " + providerKey);
        }

        String state = stateService.generateState(providerKey);

        return provider.getAuthorizationUrl()
            + "?client_id=" + urlEncode(provider.getClientId())
            + "&redirect_uri=" + urlEncode(redirectUri)
            + "&scope=" + urlEncode(provider.getScope() != null ? provider.getScope() : "")
            + "&state=" + state;
    }

    @Override
    public User handleCallback(String providerKey, String code, String state, String redirectUri) {
        if (!stateService.validateAndConsumeState(state)) {
            throw new BusinessException("Invalid or expired OAuth2 state parameter.");
        }

        OAuth2ProviderEntity provider = providerRepository.findByProviderKey(providerKey)
            .orElseThrow(() -> new BusinessException("OAuth2 provider not found: " + providerKey));

        // Exchange code for token
        OAuth2Client.TokenResponse tokenResponse = oauth2Client.exchangeToken(
            provider.getTokenUrl(), provider.getClientId(), provider.getClientSecret(),
            code, redirectUri);

        // Fetch user info
        Map<String, Object> providerUserInfo = oauth2Client.fetchUserInfo(
            provider.getUserInfoUrl(), tokenResponse.accessToken);

        // Map provider user info to normalized fields
        String[] mapped = userInfoMapper.map(providerKey, providerUserInfo);
        String providerUserId = mapped[0];
        String providerUsername = mapped[1];
        String displayName = mapped[2];

        // Generate a local username based on provider
        String localUsername = providerKey + "_" + providerUserId;

        // Find or create local user
        UserEntity userEntity = userRepository.findByUsername(localUsername).orElse(null);
        if (userEntity == null) {
            userEntity = UserEntity.builder()
                .username(localUsername)
                .displayName(displayName != null ? displayName : providerUsername)
                .type("consumer_user")
                .status("active")
                .build();
            userEntity = userRepository.save(userEntity);
            log.info("Created new OAuth2 user: {} via provider: {}", localUsername, providerKey);
        } else {
            // Update display name if changed
            if (displayName != null && !displayName.equals(userEntity.getDisplayName())) {
                userEntity.setDisplayName(displayName);
                userRepository.save(userEntity);
            }
            // Check if user is disabled
            if ("disabled".equals(userEntity.getStatus())) {
                throw new BusinessException("User account is disabled.");
            }
        }

        return User.builder()
            .name(userEntity.getUsername())
            .displayName(userEntity.getDisplayName())
            .employeeId(userEntity.getEmployeeId())
            .type(userEntity.getType())
            .status(userEntity.getStatus())
            .build();
    }

    @Override
    public List<OAuth2ProviderEntity> getEnabledProviders() {
        return providerRepository.findByEnabledTrue();
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
```

- [ ] **Step 6: 验证编译通过**

Run: `cd backend && mvn compile -pl console -am -q`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add backend/console/src/main/java/com/alibaba/higress/console/service/oauth2/ \
        backend/console/src/main/java/com/alibaba/higress/console/service/OAuth2Service.java \
        backend/console/src/main/java/com/alibaba/higress/console/service/OAuth2ServiceImpl.java
git commit -m "feat: add OAuth2 core services - client, state management, user mapping"
```

---

## Task 6: 实现用户管理和 SSO 配置服务

**Files:**
- Create: `backend/console/src/main/java/com/alibaba/higress/console/service/UserService.java`
- Create: `backend/console/src/main/java/com/alibaba/higress/console/service/UserServiceImpl.java`
- Create: `backend/console/src/main/java/com/alibaba/higress/console/service/SsoConfigService.java`

- [ ] **Step 1: 创建 UserService**

```java
/*
 * Copyright (c) 2022-2026 Alibaba Group Holding Ltd.
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
package com.alibaba.higress.console.service;

import java.util.List;

import com.alibaba.higress.console.model.User;

public interface UserService {

    List<User> listUsers();

    User getUser(String username);

    User updateUserStatus(String username, String status);

    void deleteUser(String username);

    User findByUsername(String username);
}
```

`UserServiceImpl.java`:
```java
/*
 * Copyright (c) 2022-2026 Alibaba Group Holding Ltd.
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
package com.alibaba.higress.console.service;

import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import org.springframework.stereotype.Service;

import com.alibaba.higress.console.model.User;
import com.alibaba.higress.console.repository.UserRepository;
import com.alibaba.higress.console.repository.entity.UserEntity;
import com.alibaba.higress.sdk.exception.NotFoundException;

@Service
public class UserServiceImpl implements UserService {

    @Resource
    private UserRepository userRepository;

    @Override
    public List<User> listUsers() {
        return userRepository.findAll().stream()
            .map(this::toModel)
            .collect(Collectors.toList());
    }

    @Override
    public User getUser(String username) {
        return userRepository.findByUsername(username)
            .map(this::toModel)
            .orElseThrow(() -> new NotFoundException("User not found: " + username));
    }

    @Override
    public User updateUserStatus(String username, String status) {
        UserEntity entity = userRepository.findByUsername(username)
            .orElseThrow(() -> new NotFoundException("User not found: " + username));
        entity.setStatus(status);
        userRepository.save(entity);
        return toModel(entity);
    }

    @Override
    public void deleteUser(String username) {
        UserEntity entity = userRepository.findByUsername(username)
            .orElseThrow(() -> new NotFoundException("User not found: " + username));
        userRepository.delete(entity);
    }

    @Override
    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
            .map(this::toModel)
            .orElse(null);
    }

    private User toModel(UserEntity entity) {
        return User.builder()
            .name(entity.getUsername())
            .displayName(entity.getDisplayName())
            .employeeId(entity.getEmployeeId())
            .type(entity.getType())
            .status(entity.getStatus())
            .build();
    }
}
```

- [ ] **Step 2: 创建 SsoConfigService — SSO 开关与 Provider 管理**

```java
/*
 * Copyright (c) 2022-2026 Alibaba Group Holding Ltd.
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
package com.alibaba.higress.console.service;

import java.util.List;

import javax.annotation.Resource;

import org.springframework.stereotype.Service;

import com.alibaba.higress.console.repository.OAuth2ProviderRepository;
import com.alibaba.higress.console.repository.entity.OAuth2ProviderEntity;
import com.alibaba.higress.console.service.oauth2.PresetProviders;
import com.alibaba.higress.sdk.exception.ValidationException;

@Service
public class SsoConfigService {

    private static final String SSO_ENABLED_KEY = "sso.enabled";

    @Resource
    private ConfigService configService;

    @Resource
    private OAuth2ProviderRepository providerRepository;

    public boolean isSsoEnabled() {
        return configService.getBoolean(SSO_ENABLED_KEY, false);
    }

    public void setSsoEnabled(boolean enabled) {
        if (enabled) {
            // Verify at least one enabled provider exists
            List<OAuth2ProviderEntity> enabledProviders = providerRepository.findByEnabledTrue();
            if (enabledProviders.isEmpty()) {
                throw new ValidationException(
                    "Cannot enable SSO: no OAuth2 provider is configured and enabled.");
            }
        }
        configService.set(SSO_ENABLED_KEY, String.valueOf(enabled));
    }

    public List<OAuth2ProviderEntity> listProviders() {
        return providerRepository.findAll();
    }

    public OAuth2ProviderEntity getProvider(Long id) {
        return providerRepository.findById(id).orElse(null);
    }

    public OAuth2ProviderEntity addProvider(OAuth2ProviderEntity entity) {
        // If this is a preset key, fill in the preset URLs
        OAuth2ProviderEntity preset = PresetProviders.getPreset(entity.getProviderKey());
        if (preset != null) {
            entity.setAuthorizationUrl(preset.getAuthorizationUrl());
            entity.setTokenUrl(preset.getTokenUrl());
            entity.setUserInfoUrl(preset.getUserInfoUrl());
            entity.setScope(preset.getScope());
            entity.setIconUrl(preset.getIconUrl());
            entity.setIsPreset(true);
        } else {
            entity.setIsPreset(false);
        }
        return providerRepository.save(entity);
    }

    public OAuth2ProviderEntity updateProvider(OAuth2ProviderEntity entity) {
        OAuth2ProviderEntity existing = providerRepository.findById(entity.getId())
            .orElseThrow(() -> new ValidationException("Provider not found: " + entity.getId()));
        entity.setProviderKey(existing.getProviderKey());
        entity.setIsPreset(existing.getIsPreset());
        return providerRepository.save(entity);
    }

    public void deleteProvider(Long id) {
        providerRepository.deleteById(id);
    }
}
```

- [ ] **Step 3: 验证编译通过**

Run: `cd backend && mvn compile -pl console -am -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add backend/console/src/main/java/com/alibaba/higress/console/service/UserService.java \
        backend/console/src/main/java/com/alibaba/higress/console/service/UserServiceImpl.java \
        backend/console/src/main/java/com/alibaba/higress/console/service/SsoConfigService.java
git commit -m "feat: add UserService and SsoConfigService for user and SSO management"
```

---

## Task 7: 实现 Controller 层 — OAuth2 和用户管理 API

**Files:**
- Create: `backend/console/src/main/java/com/alibaba/higress/console/controller/OAuth2Controller.java`
- Create: `backend/console/src/main/java/com/alibaba/higress/console/controller/OAuth2ProviderController.java`
- Modify: `backend/console/src/main/java/com/alibaba/higress/console/controller/UserController.java`
- Create: `backend/console/src/main/java/com/alibaba/higress/console/controller/dto/OAuth2ProviderRequest.java`
- Create: `backend/console/src/main/java/com/alibaba/higress/console/controller/dto/UserStatusRequest.java`

- [ ] **Step 1: 创建 DTO 类**

`OAuth2ProviderRequest.java`:
```java
/*
 * Copyright (c) 2022-2026 Alibaba Group Holding Ltd.
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
package com.alibaba.higress.console.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "OAuth2 Provider Request")
public class OAuth2ProviderRequest {

    @Schema(description = "Display name")
    private String name;

    @Schema(description = "Provider key, e.g. github, gitlab, or custom key")
    private String providerKey;

    @Schema(description = "Authorization URL (not required for preset providers)")
    private String authorizationUrl;

    @Schema(description = "Token URL (not required for preset providers)")
    private String tokenUrl;

    @Schema(description = "User info URL (not required for preset providers)")
    private String userInfoUrl;

    @Schema(description = "OAuth2 scope")
    private String scope;

    @Schema(description = "Client ID")
    private String clientId;

    @Schema(description = "Client Secret")
    private String clientSecret;

    @Schema(description = "Icon URL")
    private String iconUrl;

    @Schema(description = "Whether the provider is enabled")
    private Boolean enabled;
}
```

`UserStatusRequest.java`:
```java
/*
 * Copyright (c) 2022-2026 Alibaba Group Holding Ltd.
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
package com.alibaba.higress.console.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "User Status Request")
public class UserStatusRequest {

    @Schema(description = "User status: active or disabled")
    private String status;
}
```

- [ ] **Step 2: 创建 OAuth2Controller — 授权流程端点**

```java
/*
 * Copyright (c) 2022-2026 Alibaba Group Holding Ltd.
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
package com.alibaba.higress.console.controller;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.alibaba.higress.console.aop.AllowAnonymous;
import com.alibaba.higress.console.controller.dto.Response;
import com.alibaba.higress.console.model.User;
import com.alibaba.higress.console.service.OAuth2Service;
import com.alibaba.higress.console.service.SsoConfigService;
import com.alibaba.higress.sdk.exception.BusinessException;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/oauth2")
@AllowAnonymous
@Tag(name = "OAuth2 APIs")
public class OAuth2Controller {

    @Resource
    private OAuth2Service oauth2Service;

    @Resource
    private SsoConfigService ssoConfigService;

    @GetMapping("/providers")
    public ResponseEntity<Response<?>> listEnabledProviders() {
        if (!ssoConfigService.isSsoEnabled()) {
            return ResponseEntity.ok(Response.success(null));
        }
        return ResponseEntity.ok(Response.success(oauth2Service.getEnabledProviders()));
    }

    @GetMapping("/authorization/{provider}")
    public void authorize(@PathVariable("provider") String providerKey,
        HttpServletRequest request, HttpServletResponse response) {
        if (!ssoConfigService.isSsoEnabled()) {
            throw new BusinessException("SSO is not enabled.");
        }
        String redirectUri = buildRedirectUri(request, providerKey);
        String authUrl = oauth2Service.buildAuthorizationUrl(providerKey, redirectUri);
        try {
            response.sendRedirect(authUrl);
        } catch (Exception e) {
            throw new BusinessException("Failed to redirect to OAuth2 provider", e);
        }
    }

    @GetMapping("/callback/{provider}")
    public void callback(@PathVariable("provider") String providerKey,
        @RequestParam(value = "code", required = false) String code,
        @RequestParam(value = "state", required = false) String state,
        @RequestParam(value = "error", required = false) String error,
        HttpServletRequest request, HttpServletResponse response) {
        if (!ssoConfigService.isSsoEnabled()) {
            redirectToLoginWithError(response, "SSO is not enabled.");
            return;
        }
        if (StringUtils.isNotEmpty(error)) {
            log.warn("OAuth2 authorization failed for provider {}: {}", providerKey, error);
            redirectToLoginWithError(response, error);
            return;
        }
        if (StringUtils.isEmpty(code) || StringUtils.isEmpty(state)) {
            redirectToLoginWithError(response, "Missing code or state parameter.");
            return;
        }
        try {
            String redirectUri = buildRedirectUri(request, providerKey);
            User user = oauth2Service.handleCallback(providerKey, code, state, redirectUri);
            // Use the existing session mechanism to save OAuth2 session
            // SessionServiceImpl will handle OAuth2-specific cookie generation
            com.alibaba.higress.console.service.SessionService sessionService =
                (com.alibaba.higress.console.service.SessionService)
                    request.getServletContext().getAttribute("sessionService");
            // Inject sessionService properly via @Resource instead
            saveSessionAndRedirect(user, request, response);
        } catch (Exception e) {
            log.error("OAuth2 callback failed for provider {}", providerKey, e);
            redirectToLoginWithError(response, e.getMessage());
        }
    }

    @Resource
    private com.alibaba.higress.console.service.SessionService sessionService;

    private void saveSessionAndRedirect(User user, HttpServletRequest request,
        HttpServletResponse response) {
        try {
            sessionService.saveOAuth2Session(response, user);
            response.sendRedirect("/");
        } catch (Exception e) {
            throw new BusinessException("Failed to save session", e);
        }
    }

    private String buildRedirectUri(HttpServletRequest request, String providerKey) {
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int port = request.getServerPort();
        String baseUrl = scheme + "://" + serverName;
        if (("http".equals(scheme) && port != 80) || ("https".equals(scheme) && port != 443)) {
            baseUrl += ":" + port;
        }
        return baseUrl + "/oauth2/callback/" + providerKey;
    }

    private void redirectToLoginWithError(HttpServletResponse response, String error) {
        try {
            response.sendRedirect("/login?oauth_error=" + java.net.URLEncoder.encode(error, "UTF-8"));
        } catch (Exception e) {
            throw new BusinessException("Failed to redirect", e);
        }
    }
}
```

- [ ] **Step 3: 创建 OAuth2ProviderController — Provider 管理**

```java
/*
 * Copyright (c) 2022-2026 Alibaba Group Holding Ltd.
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
package com.alibaba.higress.console.controller;

import java.util.List;

import javax.annotation.Resource;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alibaba.higress.console.controller.dto.OAuth2ProviderRequest;
import com.alibaba.higress.console.controller.dto.Response;
import com.alibaba.higress.console.controller.util.ControllerUtil;
import com.alibaba.higress.console.repository.entity.OAuth2ProviderEntity;
import com.alibaba.higress.console.service.SsoConfigService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/v1/oauth2-providers")
@Tag(name = "OAuth2 Provider APIs")
public class OAuth2ProviderController {

    @Resource
    private SsoConfigService ssoConfigService;

    @GetMapping
    @Operation(summary = "List all OAuth2 providers")
    public ResponseEntity<Response<List<OAuth2ProviderEntity>>> list() {
        return ResponseEntity.ok(Response.success(ssoConfigService.listProviders()));
    }

    @PostMapping
    @Operation(summary = "Add an OAuth2 provider")
    public ResponseEntity<Response<OAuth2ProviderEntity>> add(
        @RequestBody OAuth2ProviderRequest request) {
        OAuth2ProviderEntity entity = toEntity(request);
        OAuth2ProviderEntity created = ssoConfigService.addProvider(entity);
        return ControllerUtil.buildResponseEntity(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an OAuth2 provider")
    public ResponseEntity<Response<OAuth2ProviderEntity>> update(
        @PathVariable Long id, @RequestBody OAuth2ProviderRequest request) {
        OAuth2ProviderEntity entity = toEntity(request);
        entity.setId(id);
        OAuth2ProviderEntity updated = ssoConfigService.updateProvider(entity);
        return ControllerUtil.buildResponseEntity(updated);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an OAuth2 provider")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        ssoConfigService.deleteProvider(id);
        return ControllerUtil.buildSuccessResponseEntity();
    }

    @GetMapping("/sso-status")
    @Operation(summary = "Get SSO enabled status")
    public ResponseEntity<Response<Boolean>> getSsoStatus() {
        return ResponseEntity.ok(Response.success(ssoConfigService.isSsoEnabled()));
    }

    @PutMapping("/sso-status")
    @Operation(summary = "Set SSO enabled status")
    public ResponseEntity<?> setSsoStatus(@RequestBody Boolean enabled) {
        ssoConfigService.setSsoEnabled(enabled);
        return ControllerUtil.buildSuccessResponseEntity();
    }

    private OAuth2ProviderEntity toEntity(OAuth2ProviderRequest request) {
        return OAuth2ProviderEntity.builder()
            .name(request.getName())
            .providerKey(request.getProviderKey())
            .authorizationUrl(request.getAuthorizationUrl())
            .tokenUrl(request.getTokenUrl())
            .userInfoUrl(request.getUserInfoUrl())
            .scope(request.getScope())
            .clientId(request.getClientId())
            .clientSecret(request.getClientSecret())
            .iconUrl(request.getIconUrl())
            .enabled(request.getEnabled() != null ? request.getEnabled() : true)
            .build();
    }
}
```

- [ ] **Step 4: 修改 UserController — 添加用户管理接口**

在现有 `UserController.java` 中增加用户列表、状态变更、删除等接口。保持现有的 `getUserInfo` 和 `changePassword` 不变：

```java
// Add these methods to existing UserController.java

@Resource
private UserService userService;

@GetMapping("/list")
@Operation(summary = "List all users")
public ResponseEntity<Response<java.util.List<User>>> listUsers() {
    return ResponseEntity.ok(Response.success(userService.listUsers()));
}

@GetMapping("/{username}")
@Operation(summary = "Get user by username")
public ResponseEntity<Response<User>> getUser(@PathVariable String username) {
    return ControllerUtil.buildResponseEntity(userService.getUser(username));
}

@PutMapping("/{username}/status")
@Operation(summary = "Update user status")
public ResponseEntity<Response<User>> updateUserStatus(@PathVariable String username,
    @RequestBody UserStatusRequest request) {
    return ControllerUtil.buildResponseEntity(
        userService.updateUserStatus(username, request.getStatus()));
}

@DeleteMapping("/{username}")
@Operation(summary = "Delete user")
public ResponseEntity<?> deleteUser(@PathVariable String username) {
    userService.deleteUser(username);
    return ControllerUtil.buildSuccessResponseEntity();
}
```

- [ ] **Step 5: 验证编译通过**

Run: `cd backend && mvn compile -pl console -am -q`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add backend/console/src/main/java/com/alibaba/higress/console/controller/
git commit -m "feat: add OAuth2, OAuth2Provider, and User management controllers"
```

---

## Task 8: 前端 — SSO 配置管理页面和 API 服务

**Files:**
- Create: `frontend/src/services/oauth2.ts`
- Modify: `frontend/src/interfaces/user.ts`
- Modify: `frontend/src/interfaces/config.ts`
- Modify: `frontend/src/pages/system/index.tsx`
- Modify: `frontend/src/locales/zh-CN/translation.json`
- Modify: `frontend/src/locales/en-US/translation.json`

- [ ] **Step 1: 扩展前端接口定义**

修改 `frontend/src/interfaces/user.ts`，增加 OAuth2 Provider 接口和 UserInfo 扩展：

```typescript
export interface UserInfo {
  username: string;
  displayName: string;
  type?: 'user' | 'admin' | 'guest';
  avatarUrl?: string;
  employeeId?: string;
}

export interface LoginParams {
  username: string;
  password: string;
}

export interface ChangePasswordParams {
  oldPassword: string;
  newPassword: string;
}

export interface OAuth2Provider {
  id: number;
  name: string;
  providerKey: string;
  authorizationUrl: string;
  tokenUrl: string;
  userInfoUrl: string;
  scope: string;
  clientId: string;
  clientSecret: string;
  iconUrl: string;
  enabled: boolean;
  isPreset: boolean;
}

export interface UserListItem {
  name: string;
  displayName: string;
  employeeId?: string;
  type: string;
  status: string;
}
```

修改 `frontend/src/interfaces/config.ts`，增加 SSO 配置常量：

```typescript
export const SYSTEM_INITIALIZED = 'system.initialized';
export const LOGIN_PROMPT = 'login.prompt';
export const INDEX_REDIRECT_TARGET = 'index.redirect-target';

export const MODE = 'mode';

export enum Mode {
  STANDALONE = 'standalone',
  K8S = 'k8s',
}

export const SSO_ENABLED = 'sso.enabled';
```

- [ ] **Step 2: 创建 OAuth2 前端服务**

创建 `frontend/src/services/oauth2.ts`：

```typescript
import type { OAuth2Provider } from '@/interfaces/user';
import request from './request';

export async function getEnabledProviders(): Promise<OAuth2Provider[]> {
  return await request.get('/oauth2/providers');
}

export async function listProviders(): Promise<OAuth2Provider[]> {
  return await request.get('/v1/oauth2-providers');
}

export async function addProvider(data: Partial<OAuth2Provider>): Promise<OAuth2Provider> {
  return await request.post('/v1/oauth2-providers', data);
}

export async function updateProvider(id: number, data: Partial<OAuth2Provider>): Promise<OAuth2Provider> {
  return await request.put(`/v1/oauth2-providers/${id}`, data);
}

export async function deleteProvider(id: number): Promise<any> {
  return await request.delete(`/v1/oauth2-providers/${id}`);
}

export async function getSsoStatus(): Promise<boolean> {
  return await request.get('/v1/oauth2-providers/sso-status');
}

export async function setSsoStatus(enabled: boolean): Promise<any> {
  return await request.put('/v1/oauth2-providers/sso-status', enabled);
}
```

- [ ] **Step 3: 添加国际化文案**

在 `zh-CN/translation.json` 中添加 SSO 相关 key（在 JSON 根层级添加）：

```json
{
  "sso": {
    "title": "SSO 配置",
    "enabled": "启用 SSO 登录",
    "enabledTip": "启用后，登录页将展示第三方 SSO 登录按钮",
    "providerList": "OAuth2 服务提供者",
    "addProvider": "添加提供者",
    "editProvider": "编辑提供者",
    "presetTemplate": "预设模板",
    "customProvider": "自定义",
    "providerName": "显示名称",
    "providerKey": "提供者标识",
    "clientId": "Client ID",
    "clientSecret": "Client Secret",
    "clientSecretPlaceholder": "已配置，留空则不修改",
    "authorizationUrl": "授权端点 URL",
    "tokenUrl": "Token 端点 URL",
    "userInfoUrl": "用户信息端点 URL",
    "scope": "Scope",
    "iconUrl": "图标 URL",
    "enabledStatus": "已启用",
    "disabledStatus": "已禁用",
    "deleteConfirm": "确定要删除该 OAuth2 提供者吗？",
    "enableSsoFirst": "请先配置并启用至少一个 OAuth2 提供者",
    "loginWith": "使用 {{name}} 登录",
    "ssoLogin": "第三方账号登录",
    "orDivider": "或"
  },
  "userManagement": {
    "title": "用户管理",
    "username": "用户名",
    "displayName": "显示名称",
    "employeeId": "工号",
    "type": "类型",
    "status": "状态",
    "active": "正常",
    "disabled": "已禁用",
    "actions": "操作",
    "disableUser": "禁用",
    "enableUser": "启用",
    "deleteUser": "删除",
    "deleteConfirm": "确定要删除该用户吗？",
    "platformAdmin": "平台管理员",
    "consumerUser": "普通用户"
  }
}
```

在 `en-US/translation.json` 中添加对应的英文文案：

```json
{
  "sso": {
    "title": "SSO Configuration",
    "enabled": "Enable SSO Login",
    "enabledTip": "When enabled, SSO login buttons will be shown on the login page",
    "providerList": "OAuth2 Providers",
    "addProvider": "Add Provider",
    "editProvider": "Edit Provider",
    "presetTemplate": "Preset Template",
    "customProvider": "Custom",
    "providerName": "Display Name",
    "providerKey": "Provider Key",
    "clientId": "Client ID",
    "clientSecret": "Client Secret",
    "clientSecretPlaceholder": "Already configured, leave empty to keep unchanged",
    "authorizationUrl": "Authorization URL",
    "tokenUrl": "Token URL",
    "userInfoUrl": "User Info URL",
    "scope": "Scope",
    "iconUrl": "Icon URL",
    "enabledStatus": "Enabled",
    "disabledStatus": "Disabled",
    "deleteConfirm": "Are you sure you want to delete this OAuth2 provider?",
    "enableSsoFirst": "Please configure and enable at least one OAuth2 provider first",
    "loginWith": "Sign in with {{name}}",
    "ssoLogin": "SSO Login",
    "orDivider": "or"
  },
  "userManagement": {
    "title": "User Management",
    "username": "Username",
    "displayName": "Display Name",
    "employeeId": "Employee ID",
    "type": "Type",
    "status": "Status",
    "active": "Active",
    "disabled": "Disabled",
    "actions": "Actions",
    "disableUser": "Disable",
    "enableUser": "Enable",
    "deleteUser": "Delete",
    "deleteConfirm": "Are you sure you want to delete this user?",
    "platformAdmin": "Platform Admin",
    "consumerUser": "Consumer User"
  }
}
```

- [ ] **Step 4: Commit**

```bash
git add frontend/src/interfaces/ frontend/src/services/oauth2.ts frontend/src/locales/
git commit -m "feat: add frontend OAuth2 service, interfaces, and i18n for SSO"
```

---

## Task 9: 前端 — 修改登录页，添加 SSO 登录按钮

**Files:**
- Modify: `frontend/src/pages/login/index.tsx`
- Modify: `frontend/src/pages/login/index.module.css`

- [ ] **Step 1: 修改登录页组件**

在 `frontend/src/pages/login/index.tsx` 中，在现有的 `LoginForm` 组件下方添加 SSO 登录按钮区域：

```tsx
import logo from '@/assets/logo.png';
import LanguageDropdown from '@/components/LanguageDropdown';
import { LOGIN_PROMPT, SYSTEM_INITIALIZED } from '@/interfaces/config';
import type { LoginParams, UserInfo, OAuth2Provider } from '@/interfaces/user';
import { login } from '@/services';
import { getEnabledProviders } from '@/services/oauth2';
import store from '@/store';
import { LockOutlined, UserOutlined } from '@ant-design/icons';
import { LoginForm, ProFormCheckbox, ProFormText } from '@ant-design/pro-form';
import { Alert, Button, Divider, message, Space } from 'antd';
import { history, useAuth, useNavigate } from 'ice';
import React, { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import styles from './index.module.css';

const Login: React.FC = () => {
  const { t } = useTranslation();

  const [loginPrompt, setLoginPrompt] = useState<string>();
  const [ssoProviders, setSsoProviders] = useState<OAuth2Provider[]>([]);
  const [oauthError, setOauthError] = useState<string>();
  const [, userDispatcher] = store.useModel('user');
  const [configModel] = store.useModel('config');
  const [, setAuth] = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    const properties = configModel ? configModel.properties : {};
    if (!properties[SYSTEM_INITIALIZED]) {
      navigate('/init', { replace: true });
      return;
    }
    setLoginPrompt(properties[LOGIN_PROMPT]);

    // Check for OAuth error from callback redirect
    const urlParams = new URL(window.location.href).searchParams;
    const error = urlParams.get('oauth_error');
    if (error) {
      setOauthError(decodeURIComponent(error));
      // Clean URL
      window.history.replaceState({}, '', '/login');
    }

    // Load enabled SSO providers
    getEnabledProviders()
      .then((providers) => {
        if (providers && providers.length > 0) {
          setSsoProviders(providers);
        }
      })
      .catch(() => {
        // SSO not configured, ignore
      });
  }, [configModel]);

  async function updateUserInfo(user: UserInfo) {
    userDispatcher.updateCurrentUser(user);
  }

  async function handleSubmit(values: LoginParams) {
    try {
      const user = await login(values);
      // We only support admin role at the moment.
      user.type = 'admin';
      message.success(t('login.loginSuccess'));
      setAuth({
        admin: user.type === 'admin',
        user: user.type === 'user',
      });
      await updateUserInfo(user);
      const urlParams = new URL(window.location.href).searchParams;
      let redirectUrl = urlParams.get('redirect');
      if (!redirectUrl || redirectUrl === '/login') {
        redirectUrl = '/';
      }
      history?.push(redirectUrl);
      return;
    } catch (error) {
      message.error(t('login.loginFailed'));
    }
  }

  function handleSsoLogin(providerKey: string) {
    window.location.href = `/api/oauth2/authorization/${providerKey}`;
  }

  return (
    <div className={styles.container}>
      <div className={styles['language-dropdown']}>
        <LanguageDropdown />
      </div>
      <LoginForm
        title=""
        logo={<img alt="logo" src={logo} />}
        subTitle=""
        onFinish={async (values) => {
          await handleSubmit(values as LoginParams);
        }}
        submitter={{
          searchConfig: {
            submitText: t('login.buttonText'),
          },
        }}
      >
        <ProFormText
          name="username"
          fieldProps={{
            size: 'large',
            prefix: <UserOutlined className={'prefixIcon'} />,
          }}
          placeholder={t('login.usernamePlaceholder')}
          rules={[
            {
              required: true,
              message: t('login.usernameRequired'),
            },
          ]}
        />
        <ProFormText.Password
          name="password"
          fieldProps={{
            size: 'large',
            prefix: <LockOutlined className={'prefixIcon'} />,
          }}
          placeholder={t('login.passwordPlaceholder')}
          rules={[
            {
              required: true,
              message: t('login.passwordRequired'),
            },
          ]}
        />
        {loginPrompt && (
          <div
            style={{
              marginBottom: 24,
              textAlign: 'center',
              whiteSpace: 'pre-wrap',
            }}
          >
            {loginPrompt}
          </div>
        )}
        <div
          style={{
            marginBottom: 24,
          }}
        >
          <ProFormCheckbox noStyle name="autoLogin">
            {t('login.autoLogin')}
          </ProFormCheckbox>
          <a
            style={{
              float: 'right',
            }}
          >
            {t('login.forgotPassword')}
          </a>
        </div>
      </LoginForm>

      {oauthError && (
        <Alert
          type="error"
          message={oauthError}
          style={{ maxWidth: 328, margin: '0 auto 16px' }}
          closable
          onClose={() => setOauthError(undefined)}
        />
      )}

      {ssoProviders.length > 0 && (
        <>
          <Divider style={{ maxWidth: 328, margin: '16px auto' }}>
            {t('sso.orDivider')}
          </Divider>
          <div className={styles['sso-buttons']}>
            <p className={styles['sso-label']}>{t('sso.ssoLogin')}</p>
            <Space direction="vertical" style={{ width: '100%', maxWidth: 328 }}>
              {ssoProviders.map((provider) => (
                <Button
                  key={provider.providerKey}
                  block
                  size="large"
                  onClick={() => handleSsoLogin(provider.providerKey)}
                  icon={provider.iconUrl ? (
                    <img src={provider.iconUrl} alt={provider.name} style={{ width: 18, height: 18 }} />
                  ) : undefined}
                >
                  {t('sso.loginWith', { name: provider.name })}
                </Button>
              ))}
            </Space>
          </div>
        </>
      )}
    </div>
  );
};

export default Login;
```

- [ ] **Step 2: 添加 SSO 按钮样式**

在 `frontend/src/pages/login/index.module.css` 中添加：

```css
.sso-buttons {
  max-width: 328px;
  margin: 0 auto;
}

.sso-label {
  text-align: center;
  color: rgba(0, 0, 0, 0.45);
  margin-bottom: 16px;
}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/pages/login/
git commit -m "feat: add SSO login buttons to login page"
```

---

## Task 10: 前端 — 系统设置页增加 SSO 配置 Tab

**Files:**
- Modify: `frontend/src/pages/system/index.tsx`

- [ ] **Step 1: 修改系统设置页，增加 SSO 配置 Tab**

先读取现有系统设置页面，然后在其基础上添加 SSO 配置选项卡。由于需要根据实际页面结构调整，此步骤展示核心功能组件代码：

在系统设置页面中增加一个 "SSO 配置" Tab，包含：
- SSO 全局开关（Switch 组件）
- OAuth2 Provider 列表（ProTable）
- 添加/编辑 Provider 的 Drawer 或 Modal
- 预设模板选择（GitHub / GitLab 快速配置）

主要逻辑：
1. 加载时调用 `getSsoStatus()` 获取开关状态，`listProviders()` 获取 Provider 列表
2. 切换开关时调用 `setSsoStatus(enabled)`
3. 添加 Provider 时根据选择的模板自动填充 URL（`authorizationUrl`、`tokenUrl`、`userInfoUrl`）
4. 编辑 Provider 时 `clientSecret` 显示为脱敏占位符
5. 删除 Provider 时二次确认

- [ ] **Step 2: Commit**

```bash
git add frontend/src/pages/system/
git commit -m "feat: add SSO configuration tab to system settings page"
```

---

## Task 11: 前端 — 用户管理页面

**Files:**
- Create: `frontend/src/pages/user/list.tsx`
- Modify: `frontend/src/pages/_defaultProps.tsx`（添加路由）
- Modify: `frontend/src/services/user.ts`（添加用户管理 API）

- [ ] **Step 1: 扩展 user.ts 服务**

在 `frontend/src/services/user.ts` 中添加：

```typescript
export async function listUsers(): Promise<any> {
  return await request.get('/user/list');
}

export async function getUserDetail(username: string): Promise<any> {
  return await request.get(`/user/${username}`);
}

export async function updateUserStatus(username: string, status: string): Promise<any> {
  return await request.put(`/user/${username}/status`, { status });
}

export async function deleteUser(username: string): Promise<any> {
  return await request.delete(`/user/${username}`);
}
```

- [ ] **Step 2: 创建用户管理列表页**

创建 `frontend/src/pages/user/list.tsx`，使用 ProTable 组件展示用户列表：
- 列：用户名、显示名称、工号、类型、状态、操作
- 操作列：启用/禁用按钮、删除按钮
- 支持按用户名搜索

- [ ] **Step 3: 添加路由**

在 `frontend/src/pages/_defaultProps.tsx` 的 routes 中添加用户管理路由：

```tsx
{
  name: 'menu.userManagement',
  path: '/user/list',
  icon: <TeamOutlined />,
},
```

需要在文件顶部 import `TeamOutlined`。

- [ ] **Step 4: Commit**

```bash
git add frontend/src/pages/user/list.tsx frontend/src/pages/_defaultProps.tsx frontend/src/services/user.ts
git commit -m "feat: add user management list page and routes"
```

---

## Task 12: 更新 WebMvcInitializer 和 springdoc 路径配置

**Files:**
- Modify: `backend/console/src/main/java/com/alibaba/higress/console/WebMvcInitializer.java`
- Modify: `backend/console/src/main/resources/application.properties`

- [ ] **Step 1: 在 API 路径前缀中添加 /oauth2/**

修改 `WebMvcInitializer.java` 中的 `API_PATH_PREFIXES`：

```java
private static final List<String> API_PATH_PREFIXES = Arrays.asList("/v1/", "/oauth2/");
```

- [ ] **Step 2: 更新 springdoc 路径匹配**

修改 `application.properties` 中的 `springdoc.pathsToMatch`：

```properties
springdoc.pathsToMatch=/v1/**,/session/**,/dashboard/**,/system/**,/user/**,/oauth2/**
```

- [ ] **Step 3: Commit**

```bash
git add backend/console/src/main/java/com/alibaba/higress/console/WebMvcInitializer.java \
        backend/console/src/main/resources/application.properties
git commit -m "feat: update API path prefixes and springdoc config for OAuth2 endpoints"
```

---

## Spec Coverage Checklist

| PRD 需求 | 对应 Task |
|---------|-----------|
| 3.1 登录页面改造 | Task 9 |
| 3.2 OAuth2 Provider 配置管理 | Task 6 (SsoConfigService) + Task 8 (前端) + Task 10 |
| 3.2 SSO 功能开关（页面配置） | Task 6 (SsoConfigService) + Task 10 |
| 3.3 OAuth2 后端接口 | Task 7 (OAuth2Controller) |
| 3.4 用户管理（User/OAuth2Provider 数据模型） | Task 2 + Task 3 + Task 6 + Task 11 |
| 3.5 Session 机制扩展 | Task 4 |
| 3.6 数据库引入 | Task 1 + Task 2 |
| API: /oauth2/providers | Task 7 |
| API: /oauth2/authorization/{provider} | Task 7 |
| API: /oauth2/callback/{provider} | Task 7 |
| API: /v1/users (list/detail/status/delete) | Task 7 + Task 11 |
| API: /v1/oauth2-providers CRUD | Task 7 |
| API: /v1/system/sso-status | Task 7 |
| 国际化（中英文） | Task 8 |
| 向后兼容（密码登录不变） | Task 4 (不修改原有逻辑) |

---

## Task 2 补充: 增加 OAuth2Account 表迁移脚本

**Files:**
- Create: `backend/console/src/main/resources/db/migration/V3__create_oauth2_account_table.sql`

- [ ] **Step 1: 创建 OAuth2Account 表迁移脚本**

```sql
-- V3__create_oauth2_account_table.sql
CREATE TABLE IF NOT EXISTS `oauth2_account` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `username` VARCHAR(64) NOT NULL,
    `provider` VARCHAR(32) NOT NULL,
    `provider_user_id` VARCHAR(128) NOT NULL,
    `provider_username` VARCHAR(64) DEFAULT NULL,
    `access_token` VARCHAR(512) DEFAULT NULL,
    `refresh_token` VARCHAR(512) DEFAULT NULL,
    `token_expires_at` DATETIME DEFAULT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_provider_user` (`provider`, `provider_user_id`),
    KEY `idx_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

- [ ] **Step 2: Commit**

```bash
git add backend/console/src/main/resources/db/migration/V3__create_oauth2_account_table.sql
git commit -m "feat: add OAuth2Account table migration script"
```

---

## Task 3 补充: 增加 OAuth2Account Entity 和 Repository

**Files:**
- Create: `backend/console/src/main/java/com/alibaba/higress/console/repository/entity/OAuth2AccountEntity.java`
- Create: `backend/console/src/main/java/com/alibaba/higress/console/repository/OAuth2AccountRepository.java`

- [ ] **Step 1: 创建 OAuth2AccountEntity**

```java
/*
 * Copyright (c) 2022-2026 Alibaba Group Holding Ltd.
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
package com.alibaba.higress.console.repository.entity;

import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "oauth2_account")
public class OAuth2AccountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String username;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(nullable = false, length = 128)
    private String providerUserId;

    @Column(length = 64)
    private String providerUsername;

    @Column(length = 512)
    private String accessToken;

    @Column(length = 512)
    private String refreshToken;

    private LocalDateTime tokenExpiresAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 2: 创建 OAuth2AccountRepository**

```java
/*
 * Copyright (c) 2022-2026 Alibaba Group Holding Ltd.
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
package com.alibaba.higress.console.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.alibaba.higress.console.repository.entity.OAuth2AccountEntity;

public interface OAuth2AccountRepository extends JpaRepository<OAuth2AccountEntity, Long> {

    Optional<OAuth2AccountEntity> findByProviderAndProviderUserId(String provider, String providerUserId);

    List<OAuth2AccountEntity> findByUsername(String username);
}
```

- [ ] **Step 3: Commit**

```bash
git add backend/console/src/main/java/com/alibaba/higress/console/repository/entity/OAuth2AccountEntity.java \
        backend/console/src/main/java/com/alibaba/higress/console/repository/OAuth2AccountRepository.java
git commit -m "feat: add OAuth2Account entity and repository"
```

---

## Task 5 补充: OAuth2Client 增加 Token 刷新方法，OAuth2ServiceImpl 存储 OAuth2 账号

在 Task 5 的 `OAuth2Client.java` 中增加 `refreshToken` 方法：

```java
public TokenResponse refreshToken(String tokenUrl, String clientId, String clientSecret,
    String refreshToken) {
    JSONObject body = new JSONObject();
    body.put("client_id", clientId);
    body.put("client_secret", clientSecret);
    body.put("refresh_token", refreshToken);
    body.put("grant_type", "refresh_token");

    HttpPost post = new HttpPost(tokenUrl);
    post.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
    post.setHeader(HttpHeaders.ACCEPT, "application/json");
    post.setEntity(new StringEntity(body.toJSONString(), "UTF-8"));

    try (CloseableHttpClient httpClient = HttpClients.createDefault();
         CloseableHttpResponse response = httpClient.execute(post)) {
        String responseBody = EntityUtils.toString(response.getEntity());
        if (response.getStatusLine().getStatusCode() != 200) {
            throw new BusinessException("Token refresh failed: " + responseBody);
        }
        JSONObject json = JSON.parseObject(responseBody);
        return new TokenResponse(
            json.getString("access_token"),
            json.getString("refresh_token"),
            json.getInteger("expires_in")
        );
    } catch (IOException e) {
        throw new BusinessException("Failed to refresh token", e);
    }
}
```

在 Task 5 的 `OAuth2ServiceImpl.java` 中注入 `OAuth2AccountRepository`，在 `handleCallback` 方法中存储/更新 OAuth2 账号信息：

```java
// Add to OAuth2ServiceImpl
@Resource
private OAuth2AccountRepository accountRepository;

// In handleCallback, after user creation/update, add:

// Store or update OAuth2 account binding
OAuth2AccountEntity accountEntity = accountRepository
    .findByProviderAndProviderUserId(providerKey, providerUserId)
    .orElse(OAuth2AccountEntity.builder()
        .username(localUsername)
        .provider(providerKey)
        .providerUserId(providerUserId)
        .providerUsername(providerUsername)
        .build());
accountEntity.setAccessToken(tokenResponse.accessToken);
if (tokenResponse.refreshToken != null) {
    accountEntity.setRefreshToken(tokenResponse.refreshToken);
}
if (tokenResponse.expiresIn != null) {
    accountEntity.setTokenExpiresAt(
        java.time.LocalDateTime.now().plusSeconds(tokenResponse.expiresIn));
}
accountRepository.save(accountEntity);
```

