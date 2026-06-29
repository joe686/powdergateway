# M2-1 数据库连接管理 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现多数据库连接的 CRUD 管理、AES-128 密码加密、测试连通性，以及通过 `dynamic-datasource` 在运行时动态注册/注销连接。

**Architecture:** 后端新增 `AesUtil`（加解密）、`DbConnectionService`（业务逻辑 + 动态数据源注册）、`DbConnectionController`（REST 层）、`DbConnectionInitializer`（启动时批量注册）；前端新增 `ConnectionList.vue` 页面。pom.xml 引入 `dynamic-datasource-spring-boot-starter`，`application.yml` 从 `spring.datasource` 迁移到 `spring.datasource.dynamic` 格式。

**Tech Stack:** Spring Boot 2.7.18、MyBatis-Plus 3.5.7、dynamic-datasource-spring-boot-starter 3.6.1、HikariCP、AES/ECB/PKCS5Padding、Vue 3 + Element Plus

---

## 文件结构

| 操作 | 文件路径 |
|------|---------|
| 修改 | `backend/pom.xml` |
| 修改 | `backend/src/main/resources/application.yml` |
| 修改 | `backend/src/test/resources/application-test.yml` |
| 新建 | `backend/src/main/java/com/powergateway/utils/AesUtil.java` |
| 新建 | `backend/src/test/java/com/powergateway/M21AesUtilTest.java` |
| 新建 | `backend/src/main/java/com/powergateway/model/dto/DbConnectionSaveRequest.java` |
| 新建 | `backend/src/main/java/com/powergateway/model/dto/DbConnectionVO.java` |
| 新建 | `backend/src/main/java/com/powergateway/model/dto/TestConnectionResult.java` |
| 新建 | `backend/src/main/java/com/powergateway/service/DbConnectionService.java` |
| 新建 | `backend/src/test/java/com/powergateway/M21DbConnectionServiceTest.java` |
| 新建 | `backend/src/main/java/com/powergateway/controller/DbConnectionController.java` |
| 新建 | `backend/src/test/java/com/powergateway/M21DbConnectionControllerTest.java` |
| 新建 | `backend/src/main/java/com/powergateway/config/DbConnectionInitializer.java` |
| 新建 | `frontend/src/api/db.js` |
| 新建 | `frontend/src/views/db/ConnectionList.vue` |
| 修改 | `frontend/src/router/index.js` |

---

## Task 1: 添加 Maven 依赖

**Files:**
- Modify: `backend/pom.xml`

- [ ] **Step 1: 在 pom.xml 的 `</dependencies>` 前添加三个依赖**

在 `<!-- MySQL 驱动 -->` 依赖之后插入：

```xml
        <!-- dynamic-datasource 动态多数据源（M2-1） -->
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>dynamic-datasource-spring-boot-starter</artifactId>
            <version>3.6.1</version>
        </dependency>

        <!-- PostgreSQL 驱动（M2-1） -->
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <version>42.6.0</version>
        </dependency>

        <!-- Oracle 驱动（M2-1） -->
        <dependency>
            <groupId>com.oracle.database.jdbc</groupId>
            <artifactId>ojdbc8</artifactId>
            <version>21.9.0.0</version>
            <scope>runtime</scope>
        </dependency>
```

- [ ] **Step 2: 验证依赖可以下载**

```bash
cd backend && mvn dependency:resolve -q
```

预期：BUILD SUCCESS，无 MISSING 提示。

---

## Task 2: 迁移配置文件

**Files:**
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/test/resources/application-test.yml`

- [ ] **Step 1: 替换 application.yml 的 datasource 配置**

将原有的：
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/powergateway_config?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8
    username: root
    password: qwe12345
    driver-class-name: com.mysql.cj.jdbc.Driver
```

替换为（仅替换这一段，其他配置不动）：
```yaml
spring:
  datasource:
    dynamic:
      primary: master
      strict: false
      datasource:
        master:
          url: jdbc:mysql://localhost:3306/powergateway_config?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8
          username: root
          password: qwe12345
          driver-class-name: com.mysql.cj.jdbc.Driver
```

- [ ] **Step 2: 在 application.yml 的 `sa-token:` 之前添加 AES 密钥配置**

```yaml
powergateway:
  aes:
    key: PowerGateway128K
```

- [ ] **Step 3: 替换 application-test.yml 的 datasource 配置**

将 `application-test.yml` 中的 `spring.datasource` 部分改为：

```yaml
spring:
  datasource:
    dynamic:
      primary: master
      strict: false
      datasource:
        master:
          url: jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL
          driver-class-name: org.h2.Driver
          username: sa
          password:
  sql:
    init:
      mode: always
      schema-locations: classpath:db/init-h2.sql
  autoconfigure:
    exclude:
      - org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration
      - org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration

powergateway:
  aes:
    key: PowerGateway128K

mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.nologging.NoLoggingImpl
```

- [ ] **Step 4: 运行已有测试，确认迁移无破坏**

```bash
cd backend && mvn test -Dtest=PowergatewayApplicationTests
```

预期：PASS。若出现 `DataSource` Bean 冲突，说明 `dynamic-datasource` 自动配置未正确排除默认 DataSource；检查 pom 依赖是否正确加载。

- [ ] **Step 5: 运行全量已有测试**

```bash
cd backend && mvn test
```

预期：原有 118 个测试全绿。

- [ ] **Step 6: Commit**

```bash
cd backend && git add pom.xml src/main/resources/application.yml src/test/resources/application-test.yml
git commit -m "feat(M2-1): 引入 dynamic-datasource，迁移 datasource 配置，添加 AES 密钥"
```

---

## Task 3: AesUtil TDD

**Files:**
- Create: `backend/src/test/java/com/powergateway/M21AesUtilTest.java`
- Create: `backend/src/main/java/com/powergateway/utils/AesUtil.java`

- [ ] **Step 1: 写失败测试**

新建 `backend/src/test/java/com/powergateway/M21AesUtilTest.java`：

```java
package com.powergateway;

import com.powergateway.utils.AesUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
@DisplayName("M2-1 AesUtil 加解密工具")
class M21AesUtilTest {

    private static final String TEST_KEY = "PowerGateway128K"; // 16字节

    @Test
    @DisplayName("加密后解密还原原文")
    void 加密后解密还原() {
        String original = "mySecretPassword";
        String encrypted = AesUtil.encrypt(original, TEST_KEY);
        assertNotEquals(original, encrypted);
        assertEquals(original, AesUtil.decrypt(encrypted, TEST_KEY));
    }

    @Test
    @DisplayName("中文和特殊字符_加密解密正确")
    void 中文和特殊字符_加密解密正确() {
        String original = "密码@123!测试";
        assertEquals(original, AesUtil.decrypt(AesUtil.encrypt(original, TEST_KEY), TEST_KEY));
    }

    @Test
    @DisplayName("长密码_加密解密正确")
    void 长密码_加密解密正确() {
        String original = "ThisIsAVeryLongPassword1234567890!@#$%^&*()";
        assertEquals(original, AesUtil.decrypt(AesUtil.encrypt(original, TEST_KEY), TEST_KEY));
    }

    @Test
    @DisplayName("加密结果是合法 Base64")
    void 加密结果是Base64字符串() {
        String encrypted = AesUtil.encrypt("test123", TEST_KEY);
        assertDoesNotThrow(() -> Base64.getDecoder().decode(encrypted));
    }
}
```

- [ ] **Step 2: 运行，确认编译失败（AesUtil 不存在）**

```bash
cd backend && mvn test -Dtest=M21AesUtilTest 2>&1 | head -20
```

预期：编译错误，`cannot find symbol: AesUtil`。

- [ ] **Step 3: 实现 AesUtil**

新建 `backend/src/main/java/com/powergateway/utils/AesUtil.java`：

```java
package com.powergateway.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * AES-128/ECB/PKCS5Padding 加解密工具
 * 实例方法使用配置文件中的 key；静态方法用于单元测试（无 Spring 上下文）
 */
@Component
public class AesUtil {

    @Value("${powergateway.aes.key}")
    private String key;

    public String encrypt(String plaintext) {
        return encrypt(plaintext, key);
    }

    public String decrypt(String ciphertext) {
        return decrypt(ciphertext, key);
    }

    public static String encrypt(String plaintext, String key) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("AES 加密失败", e);
        }
    }

    public static String decrypt(String ciphertext, String key) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(ciphertext));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("AES 解密失败", e);
        }
    }
}
```

- [ ] **Step 4: 运行测试，确认通过**

```bash
cd backend && mvn test -Dtest=M21AesUtilTest
```

预期：4 tests PASS。

- [ ] **Step 5: Commit**

```bash
cd backend && git add src/main/java/com/powergateway/utils/AesUtil.java src/test/java/com/powergateway/M21AesUtilTest.java
git commit -m "feat(M2-1): 实现 AesUtil AES-128 加解密工具 [TDD]"
```

---

## Task 4: DTO 类

**Files:**
- Create: `backend/src/main/java/com/powergateway/model/dto/DbConnectionSaveRequest.java`
- Create: `backend/src/main/java/com/powergateway/model/dto/DbConnectionVO.java`
- Create: `backend/src/main/java/com/powergateway/model/dto/TestConnectionResult.java`

- [ ] **Step 1: 新建 DbConnectionSaveRequest.java**

```java
package com.powergateway.model.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class DbConnectionSaveRequest {

    /** 更新时传入，新建时为 null */
    private Long id;

    @NotBlank(message = "连接名不能为空")
    private String name;

    @NotBlank(message = "数据库类型不能为空")
    private String dbType;

    @NotBlank(message = "JDBC URL 不能为空")
    private String url;

    @NotBlank(message = "用户名不能为空")
    private String username;

    /**
     * 新建时必填；更新时传 "***" 表示不修改原密码
     */
    private String password;

    @NotBlank(message = "环境不能为空")
    private String env;

    /** 连接池大小，默认 5 */
    private Integer poolSize;

    /** 连接超时（毫秒），默认 3000 */
    private Integer timeout;
}
```

- [ ] **Step 2: 新建 DbConnectionVO.java**

```java
package com.powergateway.model.dto;

import com.powergateway.model.DbConnection;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据库连接返回视图对象，password 始终脱敏为 "***"
 */
@Data
public class DbConnectionVO {

    private Long id;
    private String name;
    private String dbType;
    private String url;
    private String username;
    private String password;
    private String env;
    private Integer poolSize;
    private Integer timeout;
    private LocalDateTime createTime;

    public static DbConnectionVO from(DbConnection conn) {
        DbConnectionVO vo = new DbConnectionVO();
        vo.setId(conn.getId());
        vo.setName(conn.getName());
        vo.setDbType(conn.getDbType());
        vo.setUrl(conn.getUrl());
        vo.setUsername(conn.getUsername());
        vo.setPassword("***");
        vo.setEnv(conn.getEnv());
        vo.setPoolSize(conn.getPoolSize());
        vo.setTimeout(conn.getTimeout());
        vo.setCreateTime(conn.getCreateTime());
        return vo;
    }
}
```

- [ ] **Step 3: 新建 TestConnectionResult.java**

```java
package com.powergateway.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TestConnectionResult {
    private boolean success;
    private String message;
}
```

- [ ] **Step 4: 编译验证**

```bash
cd backend && mvn compile -q
```

预期：BUILD SUCCESS。

---

## Task 5: DbConnectionService TDD

**Files:**
- Create: `backend/src/test/java/com/powergateway/M21DbConnectionServiceTest.java`
- Create: `backend/src/main/java/com/powergateway/service/DbConnectionService.java`

- [ ] **Step 1: 写失败测试**

新建 `backend/src/test/java/com/powergateway/M21DbConnectionServiceTest.java`：

```java
package com.powergateway;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.powergateway.model.dto.DbConnectionSaveRequest;
import com.powergateway.model.dto.DbConnectionVO;
import com.powergateway.service.DbConnectionService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("M2-1 DbConnectionService")
class M21DbConnectionServiceTest {

    @Autowired
    private DbConnectionService service;

    @Autowired
    private DataSource dataSource;

    private Long savedId;

    // ── 辅助方法 ──────────────────────────────────────────
    private DbConnectionSaveRequest buildRequest(Long id) {
        DbConnectionSaveRequest req = new DbConnectionSaveRequest();
        req.setId(id);
        req.setName("测试MySQL连接");
        req.setDbType("MySQL");
        req.setUrl("jdbc:mysql://localhost:3306/test_nonexist");
        req.setUsername("root");
        req.setPassword("testPassword@123");
        req.setEnv("dev");
        req.setPoolSize(2);
        req.setTimeout(3000);
        return req;
    }

    @AfterAll
    void cleanup() {
        if (savedId != null) {
            DynamicRoutingDataSource dds = (DynamicRoutingDataSource) dataSource;
            String key = "db_" + savedId;
            if (dds.getCurrentDataSources().containsKey(key)) {
                dds.removeDataSource(key);
            }
        }
    }

    // ── 测试用例 ──────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("save_新建_密码加密存储_数据源注册")
    void save_新建_密码已加密_数据源已注册() {
        savedId = service.save(buildRequest(null));
        assertNotNull(savedId);

        DynamicRoutingDataSource dds = (DynamicRoutingDataSource) dataSource;
        assertTrue(dds.getCurrentDataSources().containsKey("db_" + savedId),
                "保存后 DynamicRoutingDataSource 中应存在对应 key");
    }

    @Test
    @Order(2)
    @DisplayName("list_返回列表_password字段脱敏为***")
    void list_密码脱敏() {
        List<DbConnectionVO> list = service.list();
        assertFalse(list.isEmpty());
        list.forEach(vo -> assertEquals("***", vo.getPassword(),
                "接口返回的 password 必须脱敏为 ***"));
    }

    @Test
    @Order(3)
    @DisplayName("save_更新_密码传***不覆盖原值")
    void save_更新_密码传星号_原密码不变() {
        DbConnectionSaveRequest req = buildRequest(savedId);
        req.setPassword("***");
        req.setName("更新后连接名");
        assertDoesNotThrow(() -> service.save(req));

        DynamicRoutingDataSource dds = (DynamicRoutingDataSource) dataSource;
        assertTrue(dds.getCurrentDataSources().containsKey("db_" + savedId),
                "更新后数据源应仍注册");
    }

    @Test
    @Order(4)
    @DisplayName("delete_软删除_数据源注销")
    void delete_软删除_数据源注销() {
        service.delete(savedId);

        DynamicRoutingDataSource dds = (DynamicRoutingDataSource) dataSource;
        assertFalse(dds.getCurrentDataSources().containsKey("db_" + savedId),
                "删除后 DynamicRoutingDataSource 中应移除对应 key");

        savedId = null; // 避免 @AfterAll 重复清理
    }
}
```

- [ ] **Step 2: 运行，确认失败（DbConnectionService 不存在）**

```bash
cd backend && mvn test -Dtest=M21DbConnectionServiceTest 2>&1 | head -20
```

预期：编译错误。

- [ ] **Step 3: 实现 DbConnectionService**

新建 `backend/src/main/java/com/powergateway/service/DbConnectionService.java`：

```java
package com.powergateway.service;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.powergateway.dao.DbConnectionMapper;
import com.powergateway.exception.BusinessException;
import com.powergateway.model.DbConnection;
import com.powergateway.model.dto.DbConnectionSaveRequest;
import com.powergateway.model.dto.DbConnectionVO;
import com.powergateway.model.dto.TestConnectionResult;
import com.powergateway.utils.AesUtil;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DbConnectionService {

    @Autowired
    private DbConnectionMapper dbConnectionMapper;

    @Autowired
    private AesUtil aesUtil;

    @Autowired
    private DataSource dataSource;

    public List<DbConnectionVO> list() {
        return dbConnectionMapper.selectList(
                new LambdaQueryWrapper<DbConnection>().orderByDesc(DbConnection::getId)
        ).stream().map(DbConnectionVO::from).collect(Collectors.toList());
    }

    public Long save(DbConnectionSaveRequest req) {
        DbConnection conn;
        if (req.getId() != null) {
            conn = dbConnectionMapper.selectById(req.getId());
            if (conn == null) throw new BusinessException(404, "连接不存在");
        } else {
            conn = new DbConnection();
        }

        conn.setName(req.getName());
        conn.setDbType(req.getDbType());
        conn.setUrl(req.getUrl());
        conn.setUsername(req.getUsername());
        conn.setEnv(req.getEnv());
        conn.setPoolSize(req.getPoolSize() != null ? req.getPoolSize() : 5);
        conn.setTimeout(req.getTimeout() != null ? req.getTimeout() : 3000);

        // 密码处理：传 "***" 表示不修改；新建时必须提供
        if (!"***".equals(req.getPassword())) {
            if (req.getPassword() == null || req.getPassword().isEmpty()) {
                if (req.getId() == null) {
                    throw new BusinessException(400, "新建连接时密码不能为空");
                }
                // 更新且未传密码：保留原值，不修改
            } else {
                conn.setPassword(aesUtil.encrypt(req.getPassword()));
            }
        }

        if (req.getId() == null) {
            dbConnectionMapper.insert(conn);
        } else {
            dbConnectionMapper.updateById(conn);
        }

        registerDataSource(conn);
        return conn.getId();
    }

    public void delete(Long id) {
        DbConnection conn = dbConnectionMapper.selectById(id);
        if (conn == null) throw new BusinessException(404, "连接不存在");
        dbConnectionMapper.deleteById(id);
        removeDataSource(id);
    }

    public TestConnectionResult testConnection(Long id) {
        DbConnection conn = dbConnectionMapper.selectById(id);
        if (conn == null) throw new BusinessException(404, "连接不存在");
        String password = aesUtil.decrypt(conn.getPassword());
        long start = System.currentTimeMillis();
        DriverManager.setLoginTimeout(3);
        try (Connection c = DriverManager.getConnection(conn.getUrl(), conn.getUsername(), password)) {
            long elapsed = System.currentTimeMillis() - start;
            return new TestConnectionResult(true, "连接成功，耗时 " + elapsed + "ms");
        } catch (Exception e) {
            return new TestConnectionResult(false, e.getMessage());
        }
    }

    /**
     * 向 DynamicRoutingDataSource 注册数据源；已存在时先移除再注册（更新场景）。
     * 供 save() 和 DbConnectionInitializer 复用。
     */
    public void registerDataSource(DbConnection conn) {
        DynamicRoutingDataSource dds = (DynamicRoutingDataSource) dataSource;
        String key = "db_" + conn.getId();
        if (dds.getCurrentDataSources().containsKey(key)) {
            dds.removeDataSource(key);
        }
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(conn.getUrl());
        config.setUsername(conn.getUsername());
        config.setPassword(aesUtil.decrypt(conn.getPassword()));
        config.setDriverClassName(resolveDriverClass(conn.getDbType()));
        config.setMaximumPoolSize(conn.getPoolSize() != null ? conn.getPoolSize() : 5);
        config.setConnectionTimeout(conn.getTimeout() != null ? conn.getTimeout() : 3000L);
        config.setInitializationFailTimeout(-1); // 目标库不可达时不抛异常
        config.setMinimumIdle(0);                // 启动时不预先建连接
        HikariDataSource ds = new HikariDataSource(config);
        dds.addDataSource(key, ds);
        log.debug("[M2-1] 注册数据源: {} ({})", key, conn.getName());
    }

    private void removeDataSource(Long id) {
        DynamicRoutingDataSource dds = (DynamicRoutingDataSource) dataSource;
        String key = "db_" + id;
        if (dds.getCurrentDataSources().containsKey(key)) {
            dds.removeDataSource(key);
        }
    }

    private String resolveDriverClass(String dbType) {
        switch (dbType) {
            case "MySQL":      return "com.mysql.cj.jdbc.Driver";
            case "Oracle":     return "oracle.jdbc.OracleDriver";
            case "PostgreSQL": return "org.postgresql.Driver";
            default: throw new BusinessException(400, "不支持的数据库类型: " + dbType);
        }
    }
}
```

- [ ] **Step 4: 运行测试，确认通过**

```bash
cd backend && mvn test -Dtest=M21DbConnectionServiceTest
```

预期：4 tests PASS。

- [ ] **Step 5: 运行全量测试，确认无退化**

```bash
cd backend && mvn test
```

预期：全绿（原有 118 + 新增 4 = 122 个测试）。

- [ ] **Step 6: Commit**

```bash
cd backend && git add src/main/java/com/powergateway/service/DbConnectionService.java src/test/java/com/powergateway/M21DbConnectionServiceTest.java src/main/java/com/powergateway/model/dto/
git commit -m "feat(M2-1): 实现 DbConnectionService，支持 CRUD + AES 加密 + 动态数据源注册 [TDD]"
```

---

## Task 6: DbConnectionController + 集成测试

**Files:**
- Create: `backend/src/main/java/com/powergateway/controller/DbConnectionController.java`
- Create: `backend/src/test/java/com/powergateway/M21DbConnectionControllerTest.java`

- [ ] **Step 1: 先写集成测试（失败）**

新建 `backend/src/test/java/com/powergateway/M21DbConnectionControllerTest.java`：

```java
package com.powergateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("M2-1 DbConnectionController HTTP 层")
class M21DbConnectionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String token;
    private Long createdId;

    @BeforeAll
    void login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"Admin@123\"}"))
                .andReturn();
        token = JsonPath.read(result.getResponse().getContentAsString(), "$.data.token");
    }

    private Map<String, Object> baseConnBody(String name) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("dbType", "MySQL");
        body.put("url", "jdbc:mysql://localhost:3306/test_nonexist");
        body.put("username", "root");
        body.put("password", "pass@123");
        body.put("env", "dev");
        return body;
    }

    @Test
    @Order(1)
    @DisplayName("POST /api/db/save 新建_返回 id")
    void save_新建_返回id() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/db/save")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(baseConnBody("Controller测试连接"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        createdId = ((Number) JsonPath.read(
                result.getResponse().getContentAsString(), "$.data")).longValue();
    }

    @Test
    @Order(2)
    @DisplayName("GET /api/db/list 返回数组")
    void list_返回Result数组() throws Exception {
        mockMvc.perform(get("/api/db/list").header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @Order(3)
    @DisplayName("POST /api/db/{id}/test 返回 success + message 字段")
    void testConnection_返回TestResult结构() throws Exception {
        mockMvc.perform(post("/api/db/" + createdId + "/test")
                .header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.success").isBoolean())
                .andExpect(jsonPath("$.data.message").isString());
    }

    @Test
    @Order(4)
    @DisplayName("DELETE /api/db/{id} 删除_返回 200")
    void delete_返回200() throws Exception {
        mockMvc.perform(delete("/api/db/" + createdId)
                .header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(5)
    @DisplayName("POST /api/db/save 缺少必填字段_返回 400")
    void save_缺少必填字段_返回400() throws Exception {
        mockMvc.perform(post("/api/db/save")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }
}
```

- [ ] **Step 2: 运行，确认失败（DbConnectionController 不存在）**

```bash
cd backend && mvn test -Dtest=M21DbConnectionControllerTest 2>&1 | head -20
```

预期：编译错误。

- [ ] **Step 3: 实现 DbConnectionController**

新建 `backend/src/main/java/com/powergateway/controller/DbConnectionController.java`：

```java
package com.powergateway.controller;

import com.powergateway.common.Result;
import com.powergateway.model.dto.DbConnectionSaveRequest;
import com.powergateway.model.dto.DbConnectionVO;
import com.powergateway.model.dto.TestConnectionResult;
import com.powergateway.service.DbConnectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/db")
@Tag(name = "数据库连接管理", description = "M2-1 多数据源配置管理")
public class DbConnectionController {

    @Autowired
    private DbConnectionService dbConnectionService;

    @GetMapping("/list")
    @Operation(summary = "查询连接列表")
    public Result<List<DbConnectionVO>> list() {
        return Result.success(dbConnectionService.list());
    }

    @PostMapping("/save")
    @Operation(summary = "新建或更新连接")
    public Result<Long> save(@Valid @RequestBody DbConnectionSaveRequest request) {
        return Result.success(dbConnectionService.save(request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除连接")
    public Result<Void> delete(@PathVariable Long id) {
        dbConnectionService.delete(id);
        return Result.success();
    }

    @PostMapping("/{id}/test")
    @Operation(summary = "测试连通性")
    public Result<TestConnectionResult> testConnection(@PathVariable Long id) {
        return Result.success(dbConnectionService.testConnection(id));
    }
}
```

- [ ] **Step 4: 运行 Controller 测试，确认通过**

```bash
cd backend && mvn test -Dtest=M21DbConnectionControllerTest
```

预期：5 tests PASS。

- [ ] **Step 5: 运行全量测试**

```bash
cd backend && mvn test
```

预期：全绿（原有 118 + 新增 4+5 = 127 个测试）。

- [ ] **Step 6: Commit**

```bash
cd backend && git add src/main/java/com/powergateway/controller/DbConnectionController.java src/test/java/com/powergateway/M21DbConnectionControllerTest.java
git commit -m "feat(M2-1): 实现 DbConnectionController，4个接口 HTTP 层 [TDD]"
```

---

## Task 7: DbConnectionInitializer（启动时注册）

**Files:**
- Create: `backend/src/main/java/com/powergateway/config/DbConnectionInitializer.java`

- [ ] **Step 1: 实现 DbConnectionInitializer**

新建 `backend/src/main/java/com/powergateway/config/DbConnectionInitializer.java`：

```java
package com.powergateway.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.powergateway.dao.DbConnectionMapper;
import com.powergateway.model.DbConnection;
import com.powergateway.service.DbConnectionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 应用启动时将数据库中全量未删除的连接注册到 DynamicRoutingDataSource。
 * 即使某个连接的目标库当前不可达，也不会影响应用启动（HikariCP initializationFailTimeout=-1）。
 */
@Slf4j
@Component
public class DbConnectionInitializer implements ApplicationRunner {

    @Autowired
    private DbConnectionMapper dbConnectionMapper;

    @Autowired
    private DbConnectionService dbConnectionService;

    @Override
    public void run(ApplicationArguments args) {
        List<DbConnection> connections = dbConnectionMapper.selectList(
                new LambdaQueryWrapper<DbConnection>()
                        .eq(DbConnection::getDeleted, 0)
        );
        log.info("[M2-1] 启动时加载数据库连接 {} 条", connections.size());
        for (DbConnection conn : connections) {
            try {
                dbConnectionService.registerDataSource(conn);
                log.info("[M2-1] 注册数据源成功: db_{} ({})", conn.getId(), conn.getName());
            } catch (Exception e) {
                log.warn("[M2-1] 注册数据源 db_{} 失败，跳过: {}", conn.getId(), e.getMessage());
            }
        }
    }
}
```

- [ ] **Step 2: 运行全量测试，确认无退化**

```bash
cd backend && mvn test
```

预期：全绿。

- [ ] **Step 3: Commit**

```bash
cd backend && git add src/main/java/com/powergateway/config/DbConnectionInitializer.java
git commit -m "feat(M2-1): 实现 DbConnectionInitializer，启动时批量注册已有连接"
```

---

## Task 8: 前端页面

**Files:**
- Create: `frontend/src/api/db.js`
- Create: `frontend/src/views/db/ConnectionList.vue`
- Modify: `frontend/src/router/index.js`

- [ ] **Step 1: 新建 frontend/src/api/db.js**

```js
import request from '@/api/request'

export function listConnections() {
  return request.get('/db/list')
}

export function saveConnection(data) {
  return request.post('/db/save', data)
}

export function deleteConnection(id) {
  return request.delete(`/db/${id}`)
}

export function testConnection(id) {
  return request.post(`/db/${id}/test`)
}
```

- [ ] **Step 2: 新建 frontend/src/views/db/ 目录并创建 ConnectionList.vue**

新建 `frontend/src/views/db/ConnectionList.vue`：

```vue
<template>
  <div class="connection-list-page">
    <el-card class="page-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">数据库连接管理</span>
          <el-button type="primary" @click="handleCreate">新建连接</el-button>
        </div>
      </template>

      <el-table
        v-loading="loading"
        :data="tableData"
        border
        stripe
        style="width: 100%"
        empty-text="暂无数据"
      >
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="连接名" min-width="150" show-overflow-tooltip />
        <el-table-column prop="dbType" label="类型" width="120">
          <template #default="{ row }">
            <el-tag :type="dbTypeTag(row.dbType)" size="small">{{ row.dbType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="env" label="环境" width="80">
          <template #default="{ row }">
            <el-tag :type="envTag(row.env)" size="small">{{ row.env }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="url" label="JDBC URL" min-width="200" show-overflow-tooltip />
        <el-table-column prop="username" label="用户名" width="100" />
        <el-table-column prop="createTime" label="创建时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button type="success" link size="small" @click="handleTest(row)">测试</el-button>
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-popconfirm
              title="确认删除该连接？"
              @confirm="handleDelete(row.id)"
            >
              <template #reference>
                <el-button type="danger" link size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新建/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑连接' : '新建连接'"
      width="560px"
      @close="resetForm"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="90px"
      >
        <el-form-item label="连接名" prop="name">
          <el-input v-model="form.name" placeholder="请输入连接名" />
        </el-form-item>
        <el-form-item label="数据库类型" prop="dbType">
          <el-select v-model="form.dbType" placeholder="请选择" style="width: 100%">
            <el-option label="MySQL" value="MySQL" />
            <el-option label="Oracle" value="Oracle" />
            <el-option label="PostgreSQL" value="PostgreSQL" />
          </el-select>
        </el-form-item>
        <el-form-item label="JDBC URL" prop="url">
          <el-input v-model="form.url" placeholder="如：jdbc:mysql://host:3306/db" />
        </el-form-item>
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            :placeholder="form.id ? '不修改请留空' : '请输入密码'"
          />
        </el-form-item>
        <el-form-item label="环境" prop="env">
          <el-select v-model="form.env" placeholder="请选择" style="width: 100%">
            <el-option label="开发(dev)" value="dev" />
            <el-option label="测试(test)" value="test" />
            <el-option label="生产(prod)" value="prod" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listConnections, saveConnection, deleteConnection, testConnection } from '@/api/db'

const loading = ref(false)
const saving = ref(false)
const tableData = ref([])
const dialogVisible = ref(false)
const formRef = ref(null)

const defaultForm = () => ({
  id: null,
  name: '',
  dbType: 'MySQL',
  url: '',
  username: '',
  password: '',
  env: 'dev'
})

const form = ref(defaultForm())

const rules = {
  name:     [{ required: true, message: '请输入连接名', trigger: 'blur' }],
  dbType:   [{ required: true, message: '请选择数据库类型', trigger: 'change' }],
  url:      [{ required: true, message: '请输入 JDBC URL', trigger: 'blur' }],
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  env:      [{ required: true, message: '请选择环境', trigger: 'change' }]
}

async function loadList() {
  loading.value = true
  try {
    tableData.value = await listConnections()
  } finally {
    loading.value = false
  }
}

function handleCreate() {
  form.value = defaultForm()
  dialogVisible.value = true
}

function handleEdit(row) {
  form.value = { ...row, password: '' }
  dialogVisible.value = true
}

async function handleSave() {
  await formRef.value.validate()
  saving.value = true
  try {
    const payload = { ...form.value }
    // 编辑时密码为空 → 传 "***" 告知后端不修改
    if (form.value.id && !payload.password) {
      payload.password = '***'
    }
    await saveConnection(payload)
    ElMessage.success(form.value.id ? '更新成功' : '创建成功')
    dialogVisible.value = false
    loadList()
  } finally {
    saving.value = false
  }
}

async function handleDelete(id) {
  await deleteConnection(id)
  ElMessage.success('删除成功')
  loadList()
}

async function handleTest(row) {
  const result = await testConnection(row.id)
  if (result.success) {
    ElMessage.success(result.message)
  } else {
    ElMessage.error('连接失败：' + result.message)
  }
}

function resetForm() {
  formRef.value?.resetFields()
}

function formatTime(t) {
  if (!t) return '-'
  return String(t).replace('T', ' ').substring(0, 19)
}

function dbTypeTag(type) {
  return { MySQL: 'success', Oracle: 'warning', PostgreSQL: 'primary' }[type] || 'info'
}

function envTag(env) {
  return { prod: 'danger', test: 'warning', dev: 'info' }[env] || 'info'
}

onMounted(loadList)
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.card-title  { font-size: 16px; font-weight: 600; }
</style>
```

- [ ] **Step 3: 更新路由，将 interface/db 指向 ConnectionList.vue**

在 `frontend/src/router/index.js` 中，将：

```js
        {
          path: 'interface/db',
          name: 'DbConnection',
          component: () => import('@/views/placeholder/PlaceholderView.vue'),
          meta: { title: '数据库连接管理' }
        },
```

改为：

```js
        {
          path: 'interface/db',
          name: 'DbConnection',
          component: () => import('@/views/db/ConnectionList.vue'),
          meta: { title: '数据库连接管理' }
        },
```

- [ ] **Step 4: 前端构建验证**

```bash
cd frontend && npm run build 2>&1 | tail -10
```

预期：`built in` 或 `dist/` 成功输出，无报错。

- [ ] **Step 5: Commit**

```bash
cd frontend && git add src/api/db.js src/views/db/ConnectionList.vue src/router/index.js
git commit -m "feat(M2-1): 新增数据库连接管理页面 ConnectionList.vue"
```

---

## 验收检查清单

全部任务完成后，依次验证：

- [ ] `mvn test` 全绿（≥127 个测试）
- [ ] 启动后端 (`mvn spring-boot:run`)，访问 Swagger (`http://localhost:8080/swagger-ui.html`)，能看到"数据库连接管理"分组及 4 个接口
- [ ] 通过 Swagger 保存一条 MySQL 连接，直接查数据库确认 `password` 字段为密文（非明文）
- [ ] 调用 `GET /api/db/list`，返回中 `password` 字段为 `"***"`
- [ ] 重启后端，调用 `GET /api/db/list` 仍能看到已保存的连接（`DbConnectionInitializer` 工作正常）
- [ ] 访问前端 `http://localhost:5173/interface/db`，页面正常展示连接列表
- [ ] 前端「测试」按钮调用测试接口，返回绿/红提示
