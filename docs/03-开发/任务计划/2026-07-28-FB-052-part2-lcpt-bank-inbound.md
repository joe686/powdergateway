# v0.3.2 · SOCK-5-A/B/C/D + CR-007 · lcpt-bank 入站场景实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` to implement this plan task-by-task.

**Goal:** 交付 PG **入站** TCP Socket + HTTP 出站编排 + 功能号路由能力 · 能替代 lcpt-bank 做"渠道 TCP XML → PG 入站 → 提取 FunctionId → HTTP 出站到联机 → 收 JSON 应答 → 转 XML → 从原 TCP 连接回写渠道"完整链路 · 端到端跑通 `docs/04-测试/bank的报文参考.md` FunctionId=180345 用例。

**Spec 源**：
- [`docs/06-项目管理/待办与缺陷池.md § CR-005`](../../06-项目管理/待办与缺陷池.md#cr-005-socket--xml-报文接入闭环) SOCK-5-A/B/C/D 段
- [`docs/06-项目管理/待办与缺陷池.md § CR-007`](../../06-项目管理/待办与缺陷池.md#cr-007-功能号路由-functionid-routing)
- [`docs/06-项目管理/反馈簿.md § FB-052 + FB-058`](../../06-项目管理/反馈簿.md)
- [`docs/04-测试/bank的报文参考.md`](../../04-测试/bank的报文参考.md)(验收依据)
- v0.3.0 前置计划:[part1-lcpt-host-outbound.md](./2026-07-28-FB-052-part1-lcpt-host-outbound.md)

**决策已定**（2026-08-02 用户拍板 · 见归档 [`archived/2026-08-01-待确认问题清单.md`](../../06-项目管理/archived/2026-08-01-待确认问题清单.md)）:
- **Q1=B** · CR-007 HTTP 侧已前置到 v0.3.1 · 本计划 **SOCK-5-E 裁掉** · 只保留"复用 v0.3.1 已建的 function_id 路由表 + ChannelFunctionIdMapper"
- **Q8=A** · Eureka `selfRegister` 在本计划 Task 4 补齐(v0.3.0 已延到此)
- **Q17=A** · JSON 骨架最小 UI · 4 JSONPath 文本框 · 空则扁平模式
- **Q18=A** · bank 验收标准 = "转换功能完善" · 端到端跑通 · 字段完整
- **Q19=C(实施 A)** · function_id 字段复用(v0.3.1 已建)· v1.1+ 若需拆独立表见 CR-009 备查
- **Q20=C** · 短连接同步应答 + 配置预留长连接开关(不实装)
- **Q21=A** · FB-053 pg-testkit 多应用名 mock 并入 SOCK-5-B(本计划 Task 5)
- **Q23=A** · v0.3.1 已补 trace_id 三表 · 本计划入站编排各阶段日志加 traceId 传递(渠道 → PG 入 → PG 出 → 联机 全链路)

## Architecture

### 后端目录扩展(v0.3.0 已建 socket 包 · v0.3.2 继续扩)

```
com.powergateway.socket/
├── inbound/
│   ├── SocketInboundServer.java          # Netty ServerSocket · 监听渠道 TCP XML
│   ├── SocketInboundHandler.java         # ChannelInboundHandlerAdapter · 收到请求走编排
│   ├── SocketInboundContext.java         # ThreadLocal · 保 Channel 引用用于回写
│   └── SocketInboundConfig.java          # port + framing + charset 配置
├── outbound/
│   ├── HttpOutboundExecutor.java         # SOCK-5-B · Feign/RestTemplate + Eureka discover
│   └── HttpOutboundRequest.java          # 应用名 + path + method + JSON body
├── route/                                  # SOCK-5-C 编排层
│   ├── InboundSocketOrchestrator.java    # 入 Socket 触发 → 提 FunctionId → HTTP 出 → 回 XML
│   ├── JsonSkeletonRenderer.java         # SOCK-5-D · 按 JSONPath 骨架渲染 bank 数组包 head/body
│   └── JsonSkeletonConfig.java           # 4 JSONPath 字段
└── functionid/                            # SOCK-5-E · CR-007(**若 Q1=B/C 且已在 v0.3.1 做,本目录复用**)
    ├── FunctionIdRoute.java               # 实体
    ├── FunctionIdRouteMapper.java         # MyBatis-Plus
    ├── FunctionIdRouteService.java        # 路由查询 + 缓存
    ├── FunctionIdRouteController.java     # CRUD
    └── RouteController.java               # POST /api/route · HTTP 入口路由
```

### 数据模型

- `interface_config` 加字段 `function_id VARCHAR(64) NULL UNIQUE`(Q19=A/C)· 或独立表(Q19=B)
- `interface_config.config_json` 扩展:
  ```json
  {
    "inbound": {
      "port": 6500,
      "framing": "xml_boundary",
      "charset": "UTF-8"
    },
    "outbound": {
      "applicationName": "lcpt-hstc-online-query",
      "path": "/hstc/query/online/",
      "method": "POST"
    },
    "jsonSkeleton": {
      "requestBodyPath": "$[0].body",
      "requestHeadPath": "$[0].head",
      "responseBodyPath": "$[0].body",
      "responseListPath": "$[0].body.list"
    }
  }
  ```
- `interface_config.type` 枚举再扩:SELECT/INSERT/UPDATE/DELETE/SOCKET/**INBOUND_SOCKET**

### 前端目录扩展

```
frontend/src/views/interface/steps/
├── SocketConfigStep.vue                    # v0.3.0 已建 · 扩 INBOUND_SOCKET 模式
├── InboundSocketConfigStep.vue             # 或独立步骤 · 待前端决策
├── JsonSkeletonStep.vue                    # SOCK-5-D UI · **待 Q17 确认**
└── FunctionIdInput.vue                     # SOCK-5-E · InterfaceWizard 顶部或字段步骤加 functionId 输入

frontend/src/views/tools/
├── FunctionIdRouteList.vue                 # 独立路由管理页(SOCK-5-E)
└── InterfaceList.vue                       # 加 functionId 列(SOCK-5-E)
```

### pg-testkit 扩展

```
pg-testkit/src/main/java/com/powergateway/testkit/
├── eureka/
│   ├── EurekaMultiAppSelfRegister.java     # FB-053 · 模拟注册多应用名到 Eureka
│   └── EurekaMockConfig.java               # YAML 配 application 列表
└── httpmock/
    └── HttpMockServer.java                  # 简单 HTTP 应答(联机模拟)· functionId → JSON 应答
```

## Tech Stack

- Netty(v0.3.0 已引入 · 复用 codec)
- Spring Cloud OpenFeign 或 RestTemplate(项目已用 RestTemplate,verify)
- eureka-client(项目已用 REG-1)
- JsonPath `com.jayway.jsonpath:json-path:2.9.0`(新增 · JSON 骨架渲染)
- Vue 3 + Element Plus(前端 · 项目现有)

## Global Constraints

- 端口:8080 后端主服务 · 5173 前端 · pg-testkit 6500 SOCK Mock + 9999 HTTP Mock + 8761 EurekaMock(FB-053)
- 中文注释 · TDD 严格 · 一 Task 一 commit
- 测试类 `@ActiveProfiles("test")`
- 迁移 SQL `db/migration-v0.3.2-inbound-route.sql`
- **`RegistryFacade.discover("service://xxx")` 复用**(v0.3.0 已 verify · 见 backend/CLAUDE.md 关键代码地标)
- CR-007 若在 v0.3.1 已交付(Q1=B/C + Q13=A),本计划 SOCK-5-E 部分只需**复用路由表 + 补 InterfaceList functionId 列**

## Task List

### Task 1 · CR-007 复用 verify(v0.3.1 已交付)

- [ ] verify `interface_config.function_id` 字段可用
- [ ] verify `FunctionIdRouteService` + `ChannelFunctionIdMapper` 可注入
- [ ] verify `RouteController /api/route` 端到端可跑
- [ ] 不写代码 · 只做冒烟 · 若有 gap 打断本计划回补 v0.3.1
- [ ] **无 commit**

### Task 2 · SocketInboundServer 骨架(Q20=C 短连接 + 预留字段)

- [ ] `SocketInboundConfig`:port + framing(Q5=C 三选一)+ charset(Q6=B 双选)+ maxConnections + connectionMode(short 唯一实装 · long 预留字段)
- [ ] `SocketInboundServer`:
  - Netty ServerBootstrap · 从 sys_config 或 interface_config 读端口
  - Pipeline:v0.3.0 socket.codec.* 复用(三分帧 + 双编码 · 全支持)· IdleStateHandler
  - `@PostConstruct` 启动 · `@PreDestroy` 优雅停机
  - connectionMode ≠ short 抛 BusinessException("暂只支持 short 短连接")
- [ ] `SocketInboundContext`:ThreadLocal 保存 ChannelHandlerContext 用于回写 · **MDC.put("traceId", ...)** 塞入(Q23 呼应)
- [ ] 空 `SocketInboundHandler` · 收到消息 log(含 traceId)
- [ ] 单元测试 `SocketInboundServerTest`:
  - 起 server + 打桩 client 发送 XML · 三 framing 各测 · 断言 log 有 · 断开无泄露
  - connectionMode=long 抛异常
  - traceId 从 Filter 或 Handler 生成后落 MDC
- [ ] **Commit**：`feat(sock): SOCK-5-A 入站 Socket Server 骨架 + traceId(v0.3.2 · #2)`

### Task 3 · InboundSocketOrchestrator 编排(核心)

- [ ] `InboundSocketOrchestrator.handle(String rawXml, Channel channel)`:
  1. dom4j 解析 rawXml → 提取 FunctionId(XPath 可配 · 默认 `//FunctionId`)
  2. **走 v0.3.1 双层机制**:`channelMapper.map(functionId)` → pgFunctionId → `routeService.lookup(pgFunctionId)` → interfaceId
  3. 加载 InterfaceConfig · verify type == INBOUND_SOCKET
  4. `FormatConverter.xmlToJson()` 把 XML 转 Map
  5. `JsonSkeletonRenderer.wrap(map, config.jsonSkeleton)` 得 bank 数组包 head/body(Task 6)
  6. **wrappedJson 里追加 `_originalFunctionId`(渠道原始 functionId)** 透传给联机(用户 Q1 补充明确要求)
  7. `HttpOutboundExecutor.exec(config.outbound, wrappedJson)` 得联机 JSON 应答(Task 4)· 附 traceId header
  8. `JsonSkeletonRenderer.unwrap(respJson, config.jsonSkeleton)` 得业务 body
  9. `FormatConverter.mapToXml(unwrapped)` 得 XML 应答
  10. 通过 `SocketInboundContext` 从原 Channel 回写(**关键 · 同步应答通道追踪**)
  11. 各阶段日志带 traceId(sys_log AOP 自动)
- [ ] `SocketInboundHandler.channelRead0()` 调 orchestrator · 出错则回写 XML error frame
- [ ] 集成测试 `InboundSocketOrchestratorIntegrationTest`:
  - Mock bank 报文 180345 → 起 pg-testkit HTTP Mock 应答固定 JSON → 端到端跑通
  - 断言 traceId 在四层日志一致(渠道入 → PG 入 → PG 出 → 联机应答)
  - 断言 `_originalFunctionId` 传到联机 mock
- [ ] **Commit**：`feat(sock): SOCK-5-C 入站编排 orchestrator + 渠道 fn 透传 + traceId(v0.3.2 · #3)`

### Task 4 · HttpOutboundExecutor + Eureka discover + selfRegister 补齐(Q8=A · 本计划做)

- [ ] `HttpOutboundExecutor.exec(applicationName, path, method, jsonBody, traceId)`:
  - `RegistryFacade.discover("service://" + applicationName)` 得 ServiceInstance 列表
  - 简单轮询(不做负载均衡)· 取第一个 UP 实例
  - RestTemplate POST · Content-Type=application/json · 3s connect / 10s read
  - Header 附 `X-Trace-Id: {traceId}` 透传
- [ ] `EurekaRegistryClient.selfRegister` 实装(从 v0.3.0 Q8=A 延期到此):
  - 通过 ApplicationInfoManager + DiscoveryClient 手动装配
  - 本地起用户 Eureka 实例(`非项目主干内容/register/`)· 联调 verify PG 出现在服务列表
- [ ] `RegistryHeartbeatScheduler` 补心跳失败告警(GAP 梳理维度 3)
- [ ] 集成测试 `HttpOutboundExecutorIntegrationTest`(需 profile=integration · 起 EurekaMock):
  - 注册两个应用名(Task 5 pg-testkit)· discover 成功 · POST 成功 · 应答正确
  - X-Trace-Id header 正确传递
- [ ] 集成测试 `EurekaSelfRegisterIntegrationTest`
- [ ] **Commit**：`feat(sock): SOCK-5-B HTTP 出站 + Eureka discover + selfRegister(v0.3.2 · #4)`

### Task 5 · pg-testkit Eureka Mock 多应用名注册(Q21=A · 并入 SOCK-5-B)

- [ ] `EurekaMultiAppSelfRegister`:配 YAML 里 applications 列表 · 启动时同一进程模拟注册多应用名到用户提供的 Eureka
- [ ] `EurekaMockConfig`:YAML
  ```yaml
  pg-testkit:
    eureka:
      server-addr: "http://localhost:8761/eureka"
      applications:
        - name: lcpt-hstc-online-query
          port: 9999
          health-path: /health
        - name: lcpt-hstc-online-tx
          port: 9998
          health-path: /health
  ```
- [ ] `HttpMockServer`:简单 Spring MVC controller · 按 URL path + functionId 应答 JSON 模板 · 端口从 config
- [ ] `pg-testkit/src/main/resources/mocks/http/180345.json` 应答样本(bank 报文样本对应)
- [ ] `mvn compile` 通过
- [ ] 单元测试:multi-app config 解析 · HttpMockServer path 匹配 · applications 依次注册
- [ ] MT-21-* 手工用例同批加"pg-testkit 多应用名 Eureka + HTTP mock"3 条(Q22=A)
- [ ] **Commit**：`feat(testkit): FB-053 Eureka 多应用名 + HTTP Mock(v0.3.2 · SOCK-5-B · #5)`

### Task 6 · SOCK-5-D · JsonSkeletonRenderer(核心)

- [ ] `JsonSkeletonConfig`:requestBodyPath / requestHeadPath / responseBodyPath / responseListPath
- [ ] `JsonSkeletonRenderer.wrap(flatMap, config)`:
  - 走 JsonPath · 把 flatMap 塞进 `$[0].body` + `$[0].head` 得 bank 数组包结构
- [ ] `JsonSkeletonRenderer.unwrap(bankJson, config)`:
  - 反向 · 从 `$[0].body`(或 `$[0].body.list`)提出业务体
- [ ] host 场景(skeleton 全空)· 走扁平模式 · 直接返回 map
- [ ] 单元测试 `JsonSkeletonRendererTest`:
  - bank wrap · 得 `[{"head":{},"body":{}}]` 结构正确
  - bank unwrap · 从 list 应答提出正确
  - host skeleton 空 · 扁平模式不变
  - JSONPath 语法错 · 抛清晰异常
- [ ] **Commit**：`feat(sock): SOCK-5-D JsonSkeletonRenderer(v0.3.2 · #6)`

### Task 7 · ExecController 分发 INBOUND_SOCKET(SOCK-5-C API 接入)

- [ ] `ExecController.dispatchByType()` 加 `case "INBOUND_SOCKET"` 分支 · 但 INBOUND_SOCKET 本身是"被动接收"· 分支只是错误提示"INBOUND_SOCKET 类型接口通过 TCP 端口触发 · 不能走 /api/exec HTTP 入口"
- [ ] `InterfaceConfigService.save()` 校验 INBOUND_SOCKET 类型必填 inbound + outbound 配置
- [ ] **Commit**：`feat(sock): INBOUND_SOCKET 类型接入 + 校验(v0.3.2 · SOCK-5-C · #7)`

### Task 8 · 前端 InterfaceWizard 支持 INBOUND_SOCKET(Q17=A · 最小 UI)

- [ ] `InterfaceWizard.vue` 目标类型下拉加 INBOUND_SOCKET
- [ ] `InboundSocketConfigStep.vue`:入站 Socket 端口 + framing(Q5=C 三选)+ charset(Q6=B 双选)+ FunctionId XPath 配置(默认 `//FunctionId`)+ connectionMode(short 唯一)
- [ ] `OutboundHttpConfigStep.vue`:应用名(带下拉 · 从 Eureka discover)+ path + method
- [ ] `JsonSkeletonStep.vue`(Q17=A):**折叠区**(不占独立 step)· 4 个 JSONPath 文本框(requestBodyPath / requestHeadPath / responseBodyPath / responseListPath)· 空则扁平模式
- [ ] 前端 vitest 用例(每 step 3-5 条)
- [ ] MT-21-* 手工用例同批(Q22=A)
- [ ] `npm run build` 通过
- [ ] **Commit**：`feat(sock): 前端 INBOUND_SOCKET 三 step + JSON 骨架折叠区 + MT 用例(v0.3.2 · #8)`

### Task 9 · CR-007 前端复用(v0.3.1 已完成 · 无需再做)

- [ ] verify v0.3.1 交付的 `InterfaceWizard.vue` PG 功能号输入 + `InterfaceList.vue` 列 + xlsx 列 均可用
- [ ] 若 INBOUND_SOCKET 类型需要额外配 PG 功能号(与 SELECT 等类型逻辑一致),仅补 vitest 用例
- [ ] **无 commit**(除非 v0.3.1 遗漏)

### Task 10 · 端到端验收(bank 报文样本 · Q18=A · 转换功能完善)

- [ ] 本地起 pg-testkit(Eureka mock + HTTP mock · Task 5 交付)
- [ ] 本地起 backend
- [ ] 建 interface_config 记录 type=INBOUND_SOCKET · function_id=`PG-180345` · inbound.port=6500 · outbound.applicationName=lcpt-hstc-online-query · path=/hstc/query/online/ · jsonSkeleton 4 路径填 bank 骨架
- [ ] 用 telnet/nc 或 pg-testkit Socket Client 发送 bank 请求 XML 到 6500
- [ ] 断言(Q18=A):
  - 端到端跑通:XML 请求 → PG 入 → JSON 出到联机 → JSON 应答 → XML 回渠道
  - 报文字段完整通过(bizHeader/bizBody 主要字段)
  - `_originalFunctionId` 传到联机 mock
  - trace_id 三表关联可查(v0.3.1 基础上)
- [ ] Screenshot 保存
- [ ] **不 commit**

### Task 11 · MT-21-* 手工用例补齐(Q22=A · 各 Task 已同批 · 本 Task 汇总)

- [ ] verify MT-21-* SOCK 入站 8-10 条已随 Task 2/3/8 补齐
- [ ] 补 MT-22-* 功能号路由(v0.3.1 已含 2 条 · 补 INBOUND_SOCKET 场景 3-4 条)
- [ ] 集成到 `docs/04-测试/v0.1.0-手工测试指南.md`
- [ ] **Commit**：`docs(test): MT-21/22-* 补齐 + v0.3.2 汇总(v0.3.2 · #11)`

### Task 12 · CHG + tag(Q4=A · 附 v0.3.2-preview)

- [ ] 变更记录追加:CHG-XXX(SOCK-5-A 入站 Socket)+ CHG-XXX(SOCK-5-B HTTP 出站 + Eureka selfRegister)+ CHG-XXX(SOCK-5-C 编排 + 渠道 fn 透传)+ CHG-XXX(SOCK-5-D JSON 骨架)
- [ ] 路线图 v0.3.2 段迁"已发布"· MVP 预览 tag 说明加"银行 Socket 场景试点可用"
- [ ] 待办池 § v0.3.2 条目勾掉 · CR-005 → 已归档 · FB-053 状态回写
- [ ] FB-052 → ✅ 已交付(v0.3.0 + v0.3.2 双 minor 联合)· FB-058 → ✅
- [ ] 基线 `v0.3.2-基线.md` 新建
- [ ] 后端 mvn test 全绿 · 前端 build + vitest 全绿 · pg-testkit compile 通过
- [ ] P0+P1 手工用例过 + MT-20/21/22-* 全套 + trace_id 追溯覆盖 SOCK 场景
- [ ] 3 处顶层索引同步
- [ ] `git tag -a v0.3.2 -m "PowerGateway v0.3.2 · lcpt-bank 入站闭环(SOCK-5-A~D)· 复用 v0.3.1 CR-007 路由"`
- [ ] `git tag -a v0.3.2-preview -m "PowerGateway v0.3.2 MVP 试点预览 · 银行 Socket 场景试点可用"`(Q4=A)
- [ ] 产品说明书追加 v0.3.2-preview 段
- [ ] **push 待用户确认**(**含 preview tag**)
- [ ] **Commit**：`docs(release): v0.3.2 + preview + CHG + 基线(#12)`

---

## Test Strategy

**单元测试(纯 Java)**：
- `JsonSkeletonRendererTest`(host 扁平 + bank 数组包)
- `FunctionIdRouteServiceTest`(缓存 + fallback)

**集成测试(H2 profile · `@ActiveProfiles("test")`)**：
- `SocketInboundServerTest`:起服 + 打桩 client · 端到端
- `InboundSocketOrchestratorIntegrationTest`:含 mock Eureka + mock HTTP
- `HttpOutboundExecutorIntegrationTest`:mock Eureka discover

**前端 vitest**：
- `InboundSocketConfigStep.test.js`
- `OutboundHttpConfigStep.test.js`
- `JsonSkeletonStep.test.js`
- `InterfaceList.test.js`(functionId 列 · Q9 相关)

**手工测试**：
- Task 10 bank 报文 180345 端到端
- MT-21/22-* 系列

**验收门槛**：
- 后端 mvn test ≥ 625 用例全绿(v0.3.0 基线 620+ · SOCK-5 新增 ~25-35)
- 前端 build + vitest ≥ 60 用例全绿
- pg-testkit compile 通过
- Task 10 端到端 bank 报文 180345 达到 Q18 拍板的验收级别

## 依赖与阻塞项

**开工阻塞**：
- **v0.3.0 tag 完成**(复用 codec/SocketClient · 三分帧双编码)
- **v0.3.1 tag 完成**(复用 CR-007 双层路由 + trace_id 三表)

**可并行 Task**：
- Task 5(pg-testkit)与 Task 3-4(orchestrator + HTTP 出站)可并行
- Task 8 前端 与 Task 3/6 后端骨架 可并行

## 预估工时(基于 v0.3.1 已含 CR-007 + trace_id)

| Task | 说明 | 人日 |
|---|---|---|
| Task 1 · CR-007 复用 verify(v0.3.1 已做) | 冒烟 | 0.5 |
| Task 2 · SocketInboundServer + traceId(Q20=C 短连接+预留)| | 4 |
| Task 3 · InboundSocketOrchestrator + 渠道 fn 透传 + traceId 链路 | 编排核心 | 4 |
| Task 4 · HttpOutboundExecutor + Eureka discover + selfRegister(Q8=A 延到此)| | 4 |
| Task 5 · pg-testkit Eureka 多应用 + HTTP Mock(Q21=A · FB-053)| | 3 |
| Task 6 · SOCK-5-D JsonSkeletonRenderer(Q17=A 4 JSONPath)| | 2 |
| Task 7 · ExecController + Service 校验 | | 1 |
| Task 8 · 前端 3 step + JSON 骨架折叠 | | 3 |
| Task 9 · CR-007 前端复用 verify | | 0.5 |
| Task 10 · 端到端验收(Q18=A)| | 2 |
| Task 11 · MT-21/22-* 用例汇总 | | 1 |
| Task 12 · CHG + tag + preview tag(Q4=A)| | 1.5 |
| **合计** | | **26-27 人日 ≈ 3.5 周**(v0.3.1 前置节省 3-5 天 · Eureka selfRegister 从 v0.3.0 挪过来 +2 天)|

## 关键风险

1. **Netty 同步应答回写**:入站 Channel 保存到 ThreadLocal 是简化设计 · 高并发时需换成 request-scoped 上下文(v0.4.0+ 优化)
2. **Eureka 服务发现兼容性**:REG-1 已 discover 完整 · v0.3.0 若做过 selfRegister,复用即可 · 否则 Task 4 需含实装
3. **JSON 骨架 JSONPath 复杂度**:bank 报文数组包结构不算复杂 · JsonPath 库覆盖 ok · 但用户如果配错骨架路径,报错要清晰
4. **FunctionId XPath 位置多变**:host `<bizHeader><FunctionId>` · bank `<bizHeader><FunctionId>` 位置相同 · 但其他系统可能不同 · 需配置化(Q1 Task 1 时的 sys_config)
5. **Task 10 验收样本一致性**:bank 报文用户已注"可能不同一笔",Q18 拍板影响严格度

## 相关文档

- v0.3.0 前置:[part1-lcpt-host-outbound.md](./2026-07-28-FB-052-part1-lcpt-host-outbound.md)
- 24 项决策归档:[archived/2026-08-01-待确认问题清单.md](../../06-项目管理/archived/2026-08-01-待确认问题清单.md)
- bank 报文样本:[bank的报文参考.md](../../04-测试/bank的报文参考.md)
- REG-1 discover 实现:`backend/src/main/java/com/powergateway/service/registry/RegistryFacade.java`
- FormatConverter 复用:`backend/src/main/java/com/powergateway/utils/FormatConverter.java`
