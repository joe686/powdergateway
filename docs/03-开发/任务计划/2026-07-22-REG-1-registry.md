# REG-1 · 注册中心集成（Nacos + Eureka）· 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 PowerGateway 能同时注册到多个 Nacos / Eureka 注册中心作为「被发现方」，转接口（DispatchService）可以通过 `service://SERVICE_NAME/path` 从注册中心动态发现外部系统；老 `port_route` 用直连 URL 的配置完全兼容。

**Architecture:** 抽出 `RegistryClient` 接口 + `NacosRegistryClient` / `EurekaRegistryClient` 两实现，`RegistryFacade` 门面聚合所有已启用客户端；数据库新增 `registry_config` 表存注册中心连接信息（AES 加密账号密码）；`port_route.port_address` 允许两种格式（完整 URL / `service://` 协议），`PortRouteService.dispatch` 转发前用 `ServiceUrlResolver` 解析；自注册在 `ApplicationReadyEvent` 触发，反向注销在 `ContextClosedEvent`；服务名默认 `POWERGATEWAY`，可在「系统管理 → 系统配置」里改（`sys_config` 复用），并提供「重新注册」按钮；心跳失败接入 `perf_alert`。

**Tech Stack:** Spring Boot 2.7.18（现状）; `nacos-client 2.2.4` + `spring-cloud-starter-alibaba-nacos-discovery 2021.0.5.0`（对齐 Spring Boot 2.7.x LTS）; `spring-cloud-starter-netflix-eureka-client 3.1.9`; Maven profile 三档（`registry-none` / `registry-nacos` / `registry-full`）; Vue 3 + Element Plus (`el-table`/`el-tag`/`el-dialog`/`el-select`/`el-tabs`/`ElMessage`); pg-testkit MockServer 复用为"外部被发现服务"集成测试。

## Global Constraints

- 所有文档、注释、提交信息、UI 文案一律中文
- 「注册中心管理」菜单归到「辅助工具」大类下（用户决策点补充），路径 `/tools/registry`；不放到「转接口」下，理由：自注册是全局能力（PG 无论提供什么接口都要能被外部发现），与转接口业务耦合会误导
- 「端口路由」编辑弹窗新增「选择服务」按钮，仅当至少存在一个已启用的注册中心时可点；未配置注册中心时按钮 disabled 并提示"请先到辅助工具 → 注册中心管理 添加"
- 保存 port_route 时，若 `port_address` 以 `service://` 开头，Service 层需校验："对应服务名能被至少一个注册中心发现"，失败则返回 400 + 明确 msg（用户决策点补充）
- 注册中心连接密码用 `AesUtil` 加密存 DB（对齐 db_connection.password 现状）
- 服务名（`self_service_name`）每台机器可改（用户决策点 4）；管理界面提供输入框；默认值 `POWERGATEWAY`
- 「重新注册」按钮放到「系统管理 → 系统配置」的"注册中心"Tab（用户决策点 2 补充），点击遍历所有 enabled=1 且 register_self=1 的记录重跑一次 register 流程；结果以 `ElMessage` 展示成功/失败明细
- 心跳失败连续 3 次 → 写 `perf_alert` 表，alertType=`REGISTRY_HEARTBEAT_FAIL`；接入现有 `AlertService` 而非自造（用户决策点 6）
- Maven profile 默认 `registry-none`（不带 nacos/eureka 客户端），保证不使用注册中心的用户构建产物不膨胀；用注册中心时打包命令加 `-Pregistry-nacos` 或 `-Pregistry-full`
- `RegistryFacade` 在 profile=registry-none 时是空实现（NoopRegistryFacade），`ServiceUrlResolver` 遇 `service://` 直接抛 `RegistryNotEnabledException`，保证老部署零影响
- 所有 Java 测试类必须加 `@ActiveProfiles("test")`；集成测试用 `@Profile("test")` 装载 `MockRegistryClient` 而非启动真实 Nacos
- TDD Red-Green-Refactor，禁止跳步
- 每个 Task 结束时 `git commit`，提交信息前缀 `feat(REG-1):` / `test(REG-1):` / `refactor(REG-1):` / `chore(REG-1):`
- 完成后追加 `CHG-023 REG-1 注册中心集成` 到 `docs/03-开发/变更记录.md`；`docs/01-需求/需求拆分与最小实现方案.md` 新增 REG-1 单元；`docs/03-开发/开发计划.md` 加行

---

## 文件清单速览

| 操作 | 路径 | 用途 |
|------|------|------|
| 新增 | `backend/src/main/resources/db/migration-reg-1-registry.sql` | 建 `registry_config` 表 + 幂等追加 sys_config 默认项 |
| 修改 | `backend/src/main/resources/db/init.sql` | DDL 追加 `registry_config` + `sys_config` 追加 `registry.self.service_name`（默认 POWERGATEWAY）等 KV |
| 新增 | `backend/src/main/java/com/powergateway/model/RegistryConfig.java` | 实体，字段见方案 §4.1 |
| 新增 | `backend/src/main/java/com/powergateway/model/dto/RegistryConfigSaveRequest.java` | 保存请求 DTO |
| 新增 | `backend/src/main/java/com/powergateway/service/registry/RegistryClient.java` | 接口，方法见方案 §3.1 |
| 新增 | `backend/src/main/java/com/powergateway/service/registry/ServiceInstance.java` | 服务实例 POJO（serviceName, ip, port, scheme, metadata） |
| 新增 | `backend/src/main/java/com/powergateway/service/registry/RegistryFacade.java` | 门面，聚合所有 enabled client |
| 新增 | `backend/src/main/java/com/powergateway/service/registry/NoopRegistryFacade.java` | profile=registry-none 时的空实现 |
| 新增 | `backend/src/main/java/com/powergateway/service/registry/nacos/NacosRegistryClient.java` | Nacos 实现（`@ConditionalOnClass(NamingService.class)`） |
| 新增 | `backend/src/main/java/com/powergateway/service/registry/eureka/EurekaRegistryClient.java` | Eureka 实现（`@ConditionalOnClass(EurekaClient.class)`） |
| 新增 | `backend/src/main/java/com/powergateway/service/registry/ServiceUrlResolver.java` | `service://` 协议解析 + 负载均衡（v1 轮询） |
| 新增 | `backend/src/main/java/com/powergateway/service/registry/SelfRegistrationRunner.java` | `ApplicationReadyEvent` 触发自注册；`ContextClosedEvent` 注销 |
| 新增 | `backend/src/main/java/com/powergateway/service/registry/RegistryHeartbeatScheduler.java` | `@Scheduled` 心跳 + 连续 3 次失败写 `perf_alert` |
| 修改 | `backend/src/main/java/com/powergateway/service/PortRouteService.java` | `dispatch` 前经 `ServiceUrlResolver.resolve()`；`saveRoute` 时校验 `service://` 服务存在 |
| 新增 | `backend/src/main/java/com/powergateway/controller/RegistryConfigController.java` | 增删改查 + 测试连通 + 服务发现预览 + 重新注册 5 个端点 |
| 修改 | `backend/src/main/java/com/powergateway/config/MenuPermission.java` | ADMIN 加 `/tools/registry`；USER 视用户是否具备"管理注册中心"权限（默认给 ADMIN） |
| 新增 | `backend/src/test/java/com/powergateway/service/registry/MockRegistryClient.java` | `@Profile("test")` 内存注册中心，供集成测试 |
| 新增 | `backend/src/test/java/com/powergateway/REG1RegistryFacadeTest.java` | 聚合 register/deregister/choose 单测 |
| 新增 | `backend/src/test/java/com/powergateway/REG1ServiceUrlResolverTest.java` | `service://SVC/api` 解析 + 轮询负载均衡 + 缓存降级 |
| 新增 | `backend/src/test/java/com/powergateway/REG1DispatchWithRegistryTest.java` | 端到端：pg-testkit MockServer 起 2 实例注册到 MockRegistryClient，PG 通过 `service://` 转发 |
| 新增 | `backend/src/test/java/com/powergateway/REG1RegistryControllerTest.java` | Controller 5 个端点 + 保存 port_route 校验 |
| 新增 | `backend/src/test/java/com/powergateway/REG1HeartbeatAlertTest.java` | 心跳失败 3 次触发 perf_alert 写入 |
| 修改 | `backend/pom.xml` | 追加 3 个 profile（`registry-none` 默认、`registry-nacos`、`registry-full`）；对应 dependencies |
| 新增 | `frontend/src/views/tools/RegistryManagement.vue` | 注册中心列表页（新增/编辑/删除/测试连通/服务预览） |
| 修改 | `frontend/src/views/convert/PortRoute.vue` | 编辑弹窗新增「选择服务」按钮 + 弹窗 `ServicePickerDialog.vue` |
| 新增 | `frontend/src/views/convert/ServicePickerDialog.vue` | 弹窗组件：按注册中心分组显示可发现服务，选择后自动填 `service://SVC/` |
| 修改 | `frontend/src/views/system/SystemConfig.vue` | 新增「注册中心」Tab + 「重新注册」按钮（弹确认框 → 调 `/api/registry/reregister-self`） |
| 新增 | `frontend/src/api/registry.js` | `listConfigs/save/delete/testConnection/previewServices/reregisterSelf` 6 方法 |
| 修改 | `frontend/src/router/index.js` | 追加 `/tools/registry`；lazy import |
| 修改 | `frontend/src/components/layout/SideMenu.vue` | 「辅助工具」下追加「注册中心管理」；`TOOLS_PATHS` 增 `/tools/registry` |
| 新增 | `frontend/tests/views/RegistryManagement.spec.js` | Vitest 组件测试 |
| 修改 | `docs/03-开发/变更记录.md` | 追加 `CHG-023` |
| 修改 | `docs/01-需求/需求拆分与最小实现方案.md` | 新增 REG-1 单元条目（阶段六） |
| 修改 | `docs/03-开发/开发计划.md` | REG-1 行 |
| 修改 | `docs/02-设计/架构说明.md` | 追加"注册中心集成"章节 |

---

## Task 1: DB Schema + 实体 + Repo（骨架）

**Files:**
- Create: `backend/src/main/resources/db/migration-reg-1-registry.sql`
- Modify: `backend/src/main/resources/db/init.sql`
- Create: `backend/src/main/java/com/powergateway/model/RegistryConfig.java`
- Create: `backend/src/main/java/com/powergateway/model/dto/RegistryConfigSaveRequest.java`
- Create: `backend/src/main/java/com/powergateway/mapper/RegistryConfigMapper.java`
- Create: `backend/src/test/java/com/powergateway/REG1SchemaTest.java`

**Interfaces:**
- `registry_config` 表：`id / type(nacos|eureka) / name / server_addr / namespace / group_name / username / password(AES) / enabled / register_self / service_name / extra_metadata / created_at / updated_at`
- `sys_config` 追加：`registry.self.service_name`（默认 `POWERGATEWAY`）、`registry.self.ip.override`（默认空自动探测）、`registry.heartbeat.interval.seconds`（默认 5）、`registry.heartbeat.fail.threshold`（默认 3）
- 幂等迁移：用存储过程/IF NOT EXISTS 检测列存在性（对齐现有 migration 风格）

- [ ] **Step 1 (Red)**：`REG1SchemaTest` 断言启动后 `registry_config` 表存在、`sys_config` 已含 4 个 registry.* KV
- [ ] **Step 2 (Green)**：写 DDL + 迁移脚本
- [ ] **Step 3**：`git commit -m "feat(REG-1): registry_config 表 + sys_config 注册中心默认项"`

---

## Task 2: `RegistryClient` 接口 + `RegistryFacade` + `NoopRegistryFacade`

**Files:**
- Create: `backend/src/main/java/com/powergateway/service/registry/RegistryClient.java`
- Create: `backend/src/main/java/com/powergateway/service/registry/ServiceInstance.java`
- Create: `backend/src/main/java/com/powergateway/service/registry/RegistryFacade.java`
- Create: `backend/src/main/java/com/powergateway/service/registry/NoopRegistryFacade.java`
- Create: `backend/src/test/java/com/powergateway/service/registry/MockRegistryClient.java`（`@Profile("test")`）
- Create: `backend/src/test/java/com/powergateway/REG1RegistryFacadeTest.java`

**Interfaces:**
- `RegistryClient`：
  - `String getType()` / `String getName()` / `boolean isConfigured()`
  - `void register(ServiceInstance self)` / `void deregister(String serviceName)`
  - `List<ServiceInstance> discover(String serviceName)`
  - `boolean heartbeat()` — 返回本次是否成功
- `RegistryFacade`：
  - `void registerSelfToAll(ServiceInstance self)`
  - `void deregisterSelfFromAll(String serviceName)`
  - `Optional<ServiceInstance> choose(String serviceName)` — 跨所有 client 聚合发现 + 轮询
  - `List<ClientStatus> statusAll()`

- [ ] **Step 1 (Red)**：以 2 个 `MockRegistryClient` 注入 facade，测试
  - `registerSelfToAll` 两 client 都调到
  - `choose` 从两 client 聚合，轮询返回不同实例
  - 一个 client `isConfigured=false` → 跳过不算失败
- [ ] **Step 2 (Green)**：实现 Facade + Noop + Mock
- [ ] **Step 3**：`git commit -m "feat(REG-1): RegistryClient 抽象 + Facade 聚合门面"`

---

## Task 3: `NacosRegistryClient` + Maven profile

**Files:**
- Create: `backend/src/main/java/com/powergateway/service/registry/nacos/NacosRegistryClient.java`
- Modify: `backend/pom.xml`（追加 3 个 profile）
- Create: `backend/src/test/java/com/powergateway/REG1NacosClientTest.java`

**Interfaces:**
- `NacosRegistryClient implements RegistryClient`，`@ConditionalOnClass(name = "com.alibaba.nacos.api.naming.NamingService")`，Spring bean 只在 profile=`registry-nacos`/`registry-full` 时装载
- 内部：`NamingService = NacosFactory.createNamingService(props)`，`registerInstance` / `deregisterInstance` / `selectInstances(serviceName, true)`
- 心跳：Nacos SDK 内部维护（`ephemeral=true`），本 client 的 `heartbeat()` 只做 `getServerStatus` 探活

- [ ] **Step 1 (Red)**：单元测试用 Mockito mock `NamingService`
  - `register` 调 `registerInstance` 参数正确（serviceName / ip / port / group / metadata）
  - `discover` 返回 List 长度 = mock 长度
  - 抛 NacosException → 转 `RegistryOperationException`
- [ ] **Step 2 (Green)**：Maven profile 加进 pom.xml；实现类
- [ ] **Step 3**：本地手工验证（可选）——若开发机有 Nacos，跑 `mvn spring-boot:run -Pregistry-nacos`
- [ ] **Step 4**：`git commit -m "feat(REG-1): NacosRegistryClient 实现 + Maven profile 三档"`

---

## Task 4: `EurekaRegistryClient`

**Files:**
- Create: `backend/src/main/java/com/powergateway/service/registry/eureka/EurekaRegistryClient.java`
- Create: `backend/src/test/java/com/powergateway/REG1EurekaClientTest.java`

**Interfaces:** 同 NacosRegistryClient 契约；内部用 `com.netflix.discovery.EurekaClient`

- [ ] **Step 1 (Red)**：单测 mock EurekaClient
- [ ] **Step 2 (Green)**：实现（profile=`registry-full` 才装载）
- [ ] **Step 3**：`git commit -m "feat(REG-1): EurekaRegistryClient 实现"`

---

## Task 5: `ServiceUrlResolver` + `PortRouteService.dispatch` 集成

**Files:**
- Create: `backend/src/main/java/com/powergateway/service/registry/ServiceUrlResolver.java`
- Modify: `backend/src/main/java/com/powergateway/service/PortRouteService.java`
- Create: `backend/src/test/java/com/powergateway/REG1ServiceUrlResolverTest.java`
- Create: `backend/src/test/java/com/powergateway/REG1DispatchWithRegistryTest.java`

**Interfaces:**
- `ServiceUrlResolver.resolve(String url) -> String`：
  - `http(s)://...` → 原样返回
  - `service://SVC/path?scheme=xx` → 通过 `RegistryFacade.choose("SVC")` 拿实例，拼 `http[s]://ip:port/path`
  - 未启用注册中心时遇 `service://` → 抛 `RegistryNotEnabledException`
  - 服务名找不到 → 抛 `ServiceInstanceNotFoundException`
- 本地缓存：`Caffeine`（30s TTL），发现失败时用缓存兜底一次；缓存也空 → 抛异常
- `PortRouteService.dispatch` 调 `forwardWithRetryBytes` 前先 `resolver.resolve(route.getPortAddress())`
- `PortRouteService.saveRoute` 收到 `port_address` 以 `service://` 开头时，先 `resolver.canResolve(url)`（不实际调发现，只校验服务名在任一 registry 里存在过），失败返回 400

- [ ] **Step 1 (Red · resolver 单测)**：
  - `http://a/b` → `http://a/b`
  - `service://SVC/api` + mock facade 返回实例 `10.0.0.1:8080` → `http://10.0.0.1:8080/api`
  - `service://SVC/api?scheme=https` → `https://10.0.0.1:8080/api`
  - facade 抛 → `ServiceInstanceNotFoundException`
  - 缓存降级：第一次成功后 mock facade 抛异常，第二次仍能返回缓存值
- [ ] **Step 2 (Green)**：实现 resolver
- [ ] **Step 3 (Red · dispatch 集成)**：`REG1DispatchWithRegistryTest`
  - pg-testkit MockServer 起端口 19001 + 19002，`MockRegistryClient.register` 2 实例
  - PG 存一条 port_route `port_address='service://TEST_SVC/echo'`
  - 调 `PortRouteService.dispatch()` 两次 → 两个 mock server 各收到 1 次（轮询）
  - MockRegistryClient 摘掉所有实例 → dispatch 应抛 `ServiceInstanceNotFoundException`（缓存过期后）
- [ ] **Step 4 (Green)**：改 dispatch 集成 resolver
- [ ] **Step 5**：`git commit -m "feat(REG-1): service:// 协议解析 + Dispatch 集成 + 缓存降级"`

---

## Task 6: 自注册 + 心跳 + 告警

**Files:**
- Create: `backend/src/main/java/com/powergateway/service/registry/SelfRegistrationRunner.java`
- Create: `backend/src/main/java/com/powergateway/service/registry/RegistryHeartbeatScheduler.java`
- Create: `backend/src/test/java/com/powergateway/REG1HeartbeatAlertTest.java`

**Interfaces:**
- `SelfRegistrationRunner @Component` 监听 `ApplicationReadyEvent`：
  - 读 `sys_config.registry.self.service_name` + 探测本机 IP + 从 `server.port` 拿端口
  - 构造 `ServiceInstance` 调 `registryFacade.registerSelfToAll(self)`
- 监听 `ContextClosedEvent`：反向 deregister
- `RegistryHeartbeatScheduler`：`@Scheduled(fixedDelayString="${registry.heartbeat.interval.seconds:5}000")`
  - 对每个 registry client 调 `heartbeat()`
  - 连续失败 `registry.heartbeat.fail.threshold`（默认 3）次 → 调 `AlertService` 写 `perf_alert`，alertType=`REGISTRY_HEARTBEAT_FAIL`

- [ ] **Step 1 (Red)**：测试
  - 启动测试上下文（`@SpringBootTest`），验证 `MockRegistryClient` 收到 `register(POWERGATEWAY, ip, port)`
  - 关闭上下文验证 `deregister`
  - 让 `MockRegistryClient.heartbeat()` 连返 false 3 次 → 断言 `perf_alert` 表新增 1 行 alertType=`REGISTRY_HEARTBEAT_FAIL`
- [ ] **Step 2 (Green)**：实现两个 Runner
- [ ] **Step 3**：`git commit -m "feat(REG-1): 自注册 + 心跳 + 失败告警"`

---

## Task 7: `RegistryConfigController` + 前端管理页

**Files:**
- Create: `backend/src/main/java/com/powergateway/controller/RegistryConfigController.java`
- Create: `backend/src/test/java/com/powergateway/REG1RegistryControllerTest.java`
- Create: `frontend/src/views/tools/RegistryManagement.vue`
- Create: `frontend/src/api/registry.js`
- Modify: `frontend/src/router/index.js`
- Modify: `frontend/src/components/layout/SideMenu.vue`
- Modify: `backend/src/main/java/com/powergateway/config/MenuPermission.java`

**Interfaces:**
- Controller：
  - `GET /api/registry/list` → List<RegistryConfig>（password 掩码返回）
  - `POST /api/registry/save` → `RegistryConfig` （新增/更新，AES 加密密码）
  - `DELETE /api/registry/{id}` → 已启用的先 disable 再 delete
  - `POST /api/registry/{id}/test` → `{ ok: bool, msg: string }` 尝试连通
  - `GET /api/registry/discover-preview?serviceName=xxx` → 跨所有 client 发现结果（用户填服务名预览）
  - `POST /api/registry/reregister-self` → 触发一次自注册流程（返回每个 client 的成功/失败）
- 前端 `RegistryManagement.vue`：表格 + 新增/编辑弹窗 + 测试连通按钮 + 服务发现预览按钮

- [ ] **Step 1 (Red)**：Controller 6 个测试用例（一端点一 case）
- [ ] **Step 2 (Green)**：Controller 实现
- [ ] **Step 3 (Red)**：Vitest 组件测试 `RegistryManagement.spec.js`
- [ ] **Step 4 (Green)**：前端页面 + api.js + 菜单挂载
- [ ] **Step 5**：`git commit -m "feat(REG-1): 注册中心管理页 + Controller + 菜单挂载"`

---

## Task 8: 端口路由「选择服务」+ 系统配置「重新注册」按钮

**Files:**
- Modify: `frontend/src/views/convert/PortRoute.vue`
- Create: `frontend/src/views/convert/ServicePickerDialog.vue`
- Modify: `frontend/src/views/system/SystemConfig.vue`

**Interfaces:**
- `PortRoute.vue` 编辑弹窗 portAddress 输入框旁按钮「选择服务」：
  - 无启用注册中心 → disabled + 悬浮 tip
  - 打开 `ServicePickerDialog` → 按 registry 分组列服务名（v-loading）
  - 选定 SVC → 回填 `service://SVC/`，用户自己补 path
- `SystemConfig.vue` 新增「注册中心」Tab（`v-if="hasRegistryTab"`）：
  - `sys_config.registry.self.service_name` 输入框（可编辑保存）
  - 「重新注册」按钮 → 弹 `ElMessageBox.confirm` 后调 `reregisterSelf()`

- [ ] **Step 1 (Red)**：Vitest
  - `PortRoute.vue` 服务选择按钮：无注册中心 disabled；有则可点开弹窗
  - `SystemConfig.vue` 「重新注册」点击 → 调 mock api
- [ ] **Step 2 (Green)**：实现
- [ ] **Step 3**：手工端到端：起本地 backend + frontend + pg-testkit（用 pg-testkit MockRegistryClient 或本地 Nacos）→ 完整流程走通
- [ ] **Step 4**：`git commit -m "feat(REG-1): 端口路由选服务 + 系统配置重新注册按钮"`

---

## Task 9: 文档定稿 + 变更记录

**Files:**
- Modify: `docs/03-开发/变更记录.md`
- Modify: `docs/01-需求/需求拆分与最小实现方案.md`
- Modify: `docs/03-开发/开发计划.md`
- Modify: `docs/02-设计/架构说明.md`
- Modify: `docs/03-开发/任务计划/README.md`

- [ ] **Step 1**：追加 `CHG-023 REG-1 注册中心集成`
- [ ] **Step 2**：需求拆分文档「阶段六」（或新阶段）新增 REG-1 单元条目
- [ ] **Step 3**：架构说明追加"注册中心集成"章节：`RegistryFacade` / `service://` 协议 / Maven profile 三档
- [ ] **Step 4**：开发计划表格加行；任务计划 README 加行
- [ ] **Step 5**：`git commit -m "docs(REG-1): 变更记录 + 需求拆分 + 架构说明 定稿"`

---

## 完成门槛

- 全部单元测试 + 集成测试绿（本单元新增 ~30 个 case）
- `mvn test -Pregistry-none` 通过（默认构建不带 nacos/eureka 客户端类，`@ConditionalOnClass` 应正确跳过实现类）
- `mvn test -Pregistry-nacos` 通过（NacosRegistryClient 装载 + 单测通过；集成测试用 MockRegistryClient 而非真实 Nacos）
- `mvn test -Pregistry-full` 通过
- 前端 `npm run build` 无报错
- 手工端到端：pg-testkit 起 mock 服务 → 用 `service://` 从 PG 转发过去成功
- 9 个 commit（Task 1~9 各一）
