# v0.3.0 · SOCK-1~4 · lcpt-host 出站场景实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` to implement this plan task-by-task. Steps use checkbox syntax.

**Goal:** 交付 PG **出站** TCP Socket + XML 报文接入完整能力,能替代 lcpt-host 做"JSON→XML 出 TCP Socket → 收 XML 应答 → 扁平化 JSON"完整链路 · 附 pg-testkit TCP Mock 简化版兜底本地演示 · 端到端跑通 `docs/04-测试/模拟host.md` FunctionId=181345 用例。

**Spec 源**：
- [`docs/06-项目管理/待办与缺陷池.md § CR-005`](../../06-项目管理/待办与缺陷池.md#cr-005-socket--xml-报文接入闭环)
- [`docs/06-项目管理/反馈簿.md § FB-052`](../../06-项目管理/反馈簿.md#fb-052-银行-socket--xml-报文场景全链路缺失--无法真实做演示minor-单版本闭环诉求)
- [`docs/04-测试/模拟host.md`](../../04-测试/模拟host.md)(验收依据)
- [`docs/06-项目管理/复盘诊断/能力GAP梳理-2026-07-28.md`](../../06-项目管理/复盘诊断/能力GAP梳理-2026-07-28.md)(维度 1/2/3)

**决策已定**（2026-08-02 用户拍板 · 见归档 [`archived/2026-08-01-待确认问题清单.md`](../../06-项目管理/archived/2026-08-01-待确认问题清单.md)）:
- **Q1=B** · v0.3.1 中间收官版 · CR-007 前置到 v0.3.1 · 本计划不做路由
- **Q5=C** · **两分帧全做完整支持** · 无默认 · 配置必填 · **用户明确"报文前 8 位报文长度 + 有编码格式选项"**(见 host 报文样本)
- **Q6=B** · **v0.3.0 双编码(UTF-8 + GBK)全做完** · 不同对接系统编码不同,需可选
- **Q7=A + Q20=C** · 短连接 · 配置 JSON 预留 `connectionMode: short|long|pooled` 字段(仅 short 实装)
- **Q8=A** · Eureka `selfRegister` 补齐延到 v0.3.2 SOCK-5-B · 本计划不做(用户希望"尽快见到,不能太晚",v0.3.2 = 尽快)
- **Q9=A** · `io.netty:netty-all:4.1.x`
- **Q10=A** · SOCK-3 MessageDebug 扁平化预览 tab 归 v0.3.0 · 保留 Task 8
- **Q11=A** · pg-testkit 只做 Netty Mock · 无 Python 兜底
- **Q12=B** · 不做内置 demo · 提供部署后手动配置指南(用户明确"完全模拟生产操作")· Task 12 改为"host 使用指南文档"
- **Q22=A** · 每 Task 交付同批附 MT-20-* 用例 · 不集中到 Task 11
- **Q23=A** · trace_id 三表补齐前置到 v0.3.1 · 本计划 AuditContext 层面无变化 · v0.3.1 交付后回补(SOCK 场景日志加 traceId)

## Architecture

**新增后端目录**：
```
com.powergateway.socket/
├── SocketClient.java             # Netty TCP Client 门面 · 短连接 · 三分帧编解码 · 双编码
├── SocketExecutor.java           # 请求编排 · 渲染 XML 模板 → send → 收 XML → parseXml + flattenMap → JSON
├── SocketExecRequest.java        # DTO · ip/port/framing/charset/timeouts/template/骨架
├── SocketExecResponse.java       # DTO · rawXml + flattened + latencyMs
├── codec/
│   ├── LengthPrefixCodec.java    # 4/8 字节大端定长头 · 长度字段宽度可配
│   ├── XmlBoundaryCodec.java     # XML 自闭合边界(根标签结束)
│   ├── FramingType.java          # enum · XML_BOUNDARY / LENGTH_PREFIX_BE4 / LENGTH_PREFIX_BE8
│   └── CharsetSupport.java       # UTF-8 + GBK 常量 + factory
└── exception/
    ├── SocketConnectException.java  # 连接失败
    └── SocketTimeoutException.java  # 读超时
```

**改造点**：
- `ExecController.dispatchByType()`(L82-108)加 `case "SOCKET"` 分支 · 调 `SocketExecutor.execute()`
- `InterfaceConfig.type` 枚举扩:`SELECT/INSERT/UPDATE/DELETE/SOCKET`
- `interface_config.config_json` socket 段结构(Q5=C + Q6=B + Q7A/Q20C):
  ```json
  {
    "socket": {
      "ip": "10.1.2.3",
      "port": 6500,
      "framing": "xml_boundary",         // 无默认 · 必填 · xml_boundary | length_prefix_be4 | length_prefix_be8
      "charset": "UTF-8",                 // 必填 · UTF-8 | GBK
      "connTimeoutMs": 3000,
      "readTimeoutMs": 10000,
      "connectionMode": "short",          // 仅 short 实装 · long/pooled 预留
      "requestTemplate": "<?xml version=\"1.0\" ...>...",
      "responseFlattenPrefix": ""
    }
  }
  ```
- **Q5=C 分帧策略**：
  - `xml_boundary` · 以根标签自闭合(如 `</Transaction>`)边界识别
  - `length_prefix_be4` · 4 字节大端定长头(原 CR-005 设计)
  - `length_prefix_be8` · **8 字节报文长度头**(用户 2026-08-02 补充 · 实际报文有此规格)
- **Q6=B 编码**：UTF-8 与 GBK 双支持 · 编码字段接口配置期必选 · 编解码器同时处理入/出方向
- 复用 `FormatConverter.parseXml + flattenMap`(现有能力,`utils/FormatConverter.java` L218-230)
- 补齐 `EurekaRegistryClient.selfRegister`(L55-60 占位)· **时机待 Q8 拍板**

**新增前端目录**：
```
frontend/src/views/interface/steps/SocketConfigStep.vue   # SOCK-2 · 接口向导 SOCKET 步骤
frontend/src/components/socket/                            # 若需要复用组件
```

**改造点**：
- `InterfaceWizard.vue` 目标类型下拉加 SOCKET(现在只有 SELECT/INSERT/UPDATE/DELETE)· 动态挂 SocketConfigStep
- `MessageDebug.vue` 追加"XML → 扁平 JSON"预览 tab(**Task 8 · 待 Q10 归属确认**)

**新增 pg-testkit 目录**：
```
pg-testkit/src/main/java/com/powergateway/testkit/socket/
├── SocketMockServer.java        # Netty ServerSocket · 加载 YAML rules · functionId→XML 模板
├── SocketMockRule.java          # YAML 映射
└── SocketMockConfig.java        # application.yml socket-mock 段
```

## Tech Stack

- **Netty** `io.netty:netty-all:4.1.x`(**依赖引入方式待 Q9 拍板**)
- Dom4j(项目已有 M1-1 用)· 复用 FormatConverter parseXml
- Jackson(项目已有)· 复用 flattenMap
- Spring Boot 2.7.18 · Sa-Token 1.37.0(exec 通道免登,SOCKET 沿用)
- JUnit 5 + Mockito(后端测试)· vitest(前端 SocketConfigStep 测试)
- Spring Cloud Netflix Eureka Client(项目已有 REG-1)

## Global Constraints

- 端口固定:后端 8080 · pg-testkit socket mock 端口从 6500 起(与 host 报文一致)· 前端 5173
- 中文注释与对话
- **测试类必须加 `@ActiveProfiles("test")`**
- **一 Task 一 commit**(memory `feedback_commit_style`)
- **TDD 严格 Red → Green → Refactor**(root CLAUDE.md 强制)
- 所有 Controller 返回 `Result<T>`(项目通用契约)
- 新增数据库字段走幂等迁移 SQL:`db/migration-v0.3.0-socket.sql`
- 涉及 `interface_config.type` 枚举扩展需同步 `sys_dict.type_options`(若存在)
- MessageDebug 扁平化 tab **待 Q10 确认归属**

## 文件结构预览

**Create(后端 · 待 Q9 依赖确认后开始)**：

| 文件 | 职责 |
|---|---|
| `backend/src/main/java/com/powergateway/socket/SocketClient.java` | Netty Bootstrap 封装 · connect + send + receive |
| `backend/src/main/java/com/powergateway/socket/SocketExecutor.java` | 编排请求模板渲染 + XML→JSON 扁平化 |
| `backend/src/main/java/com/powergateway/socket/SocketExecRequest.java` | 请求 DTO |
| `backend/src/main/java/com/powergateway/socket/SocketExecResponse.java` | 响应 DTO |
| `backend/src/main/java/com/powergateway/socket/codec/LengthPrefixCodec.java` | 4 字节大端定长头 |
| `backend/src/main/java/com/powergateway/socket/codec/XmlBoundaryCodec.java` | XML 自闭合边界(默认) |
| `backend/src/main/java/com/powergateway/socket/codec/FramingType.java` | 分帧枚举 |
| `backend/src/main/java/com/powergateway/socket/exception/SocketConnectException.java` | 连接异常 |
| `backend/src/main/java/com/powergateway/socket/exception/SocketTimeoutException.java` | 超时异常 |
| `backend/src/main/resources/db/migration-v0.3.0-socket.sql` | 老库客户升级迁移(幂等)|

**Modify(后端)**：
- `backend/pom.xml`(加 netty-all 依赖 · **待 Q9 确认**)
- `backend/src/main/java/com/powergateway/controller/ExecController.java`(L82-108 加 SOCKET 分支)
- `backend/src/main/java/com/powergateway/service/registry/eureka/EurekaRegistryClient.java`(L55-60 补 selfRegister · **待 Q8 时机**)
- `backend/src/main/resources/db/init.sql`(新库客户直接建 · 与 migration SQL 同步)

**Create(前端)**：

| 文件 | 职责 |
|---|---|
| `frontend/src/views/interface/steps/SocketConfigStep.vue` | InterfaceWizard SOCKET 目标步骤 |
| `frontend/tests/views/interface/SocketConfigStep.test.js` | vitest 用例 |

**Modify(前端)**：
- `frontend/src/views/interface/InterfaceWizard.vue`(SOCKET 目标类型 · 动态挂 SocketConfigStep)
- `frontend/src/views/tools/MessageDebug.vue`(**待 Q10 归属**:归 v0.3.0 则本计划做 · 归 v0.3.2 则本计划裁 Task 8)

**Create(pg-testkit)**：

| 文件 | 职责 |
|---|---|
| `pg-testkit/src/main/java/com/powergateway/testkit/socket/SocketMockServer.java` | Netty ServerSocket · YAML 加载 |
| `pg-testkit/src/main/java/com/powergateway/testkit/socket/SocketMockRule.java` | YAML 映射 |
| `pg-testkit/src/main/java/com/powergateway/testkit/socket/SocketMockConfig.java` | ConfigurationProperties |
| `pg-testkit/src/main/resources/socket-mock-rules.yml` | 示例 rules(FunctionId=181345 应答 XML) |
| `pg-testkit/src/test/resources/mocks/181345-response.xml` | 应答模板 |

**Create(文档 · CHG 归档)**：
- `docs/03-开发/变更记录.md`(追加 CHG-037/038)
- `docs/04-测试/v0.1.0-手工测试指南.md`(补 MT-20-* SOCK 出站用例 · **待 Q22 时机**)

---

## Task List(按依赖顺序 · 每 task 一 commit)

### Task 1 · Netty 依赖引入 + SocketClient 骨架(Red 阶段起点)

- [ ] pom.xml 加 `io.netty:netty-all:4.1.100.Final`(Q9=A)
- [ ] 创建 `com.powergateway.socket.SocketClient` 空类 · 只暴露 `send(String host, int port, byte[] payload, FramingType framing, Charset charset, int connTimeoutMs, int readTimeoutMs): byte[]` 签名 · 抛 UnsupportedOperationException
- [ ] 创建 `SocketConnectException` / `SocketTimeoutException`
- [ ] `FramingType` enum:`XML_BOUNDARY` · `LENGTH_PREFIX_BE4` · `LENGTH_PREFIX_BE8`(Q5=C 三种全支持)
- [ ] `CharsetSupport`:UTF-8 + GBK 白名单 factory · 拒绝其他编码抛 BusinessException
- [ ] 单元测试 `SocketClientTest`:抛 UnsupportedOperationException 断言(Red)+ FramingType 三值合法 + CharsetSupport 白名单
- [ ] 后端 `mvn compile` 通过
- [ ] **Commit**：`feat(sock): 引入 Netty + SocketClient 骨架 + 分帧/编码枚举(v0.3.0 · SOCK-1 · #1)`

### Task 2 · 分帧编解码器:XmlBoundaryCodec

- [ ] `XmlBoundaryCodec`:实现 Netty `ByteToMessageDecoder` + `MessageToByteEncoder<byte[]>` · 以根标签自闭合边界(根标签名可配 · 默认从 XML 头部动态识别)
- [ ] 单元测试 `XmlBoundaryCodecTest`:
  - 完整 XML 一次解码 · Green
  - 分片 XML(边界跨包)· Green
  - 无根标签结束 → 读超时(交给上游 IdleStateHandler)
  - 多帧连续 · Green
  - `<?xml` 声明多变体(见 host 报文 `<?xml  encoding=... version=...>`)容错
  - 请求侧支持无 xml 声明的裸 XML
- [ ] `mvn test` 全绿
- [ ] **Commit**：`feat(sock): XmlBoundaryCodec 分帧(v0.3.0 · SOCK-1 · #2)`

### Task 3 · 分帧编解码器:LengthPrefixCodec(4/8 字节宽度可配)

- [ ] `LengthPrefixCodec`:构造参数 `lengthFieldLength ∈ {4, 8}` · 复用 Netty `LengthFieldBasedFrameDecoder` + `LengthFieldPrepender` · 大端序
- [ ] 单元测试 `LengthPrefixCodecTest`:
  - **4 字节大端**:单帧 encode + decode · 长度头正确
  - **8 字节大端**:单帧 encode + decode · 长度头正确(用户 2026-08-02 明确"报文前 8 位报文长度"实证)
  - 多帧连续 decode
  - 超大 payload 报错(默认 10MB 上限)
  - 长度头值与实际不一致 → 抛异常
- [ ] `mvn test` 全绿
- [ ] **Commit**：`feat(sock): LengthPrefixCodec 4/8 字节双宽度支持(v0.3.0 · SOCK-1 · #3)`

### Task 4 · SocketClient 实装 + 短连接语义(Q7=A + Q20=C)

- [ ] `SocketClient.send()` 实装:
  - Netty Bootstrap · SO_KEEPALIVE=false(短连接)
  - Pipeline 按 framing 参数动态挂 XmlBoundaryCodec / LengthPrefixCodec(4 or 8)
  - Pipeline 按 charset 参数挂 CharsetSupport 得到 String→bytes / bytes→String 转换
  - IdleStateHandler(readTimeoutMs)
  - 同步等待应答:CompletableFuture.get(readTimeoutMs, MS)
  - 连接失败 → SocketConnectException · 读超时 → SocketTimeoutException
  - 连接关闭确保 EventLoopGroup 释放(避免线程泄露 · 共享 group 单例)
  - **connectionMode 字段仅识别 "short"**,其他值抛 BusinessException("暂只支持 short 短连接")· 预留 API 契约
- [ ] 单元测试 `SocketClientIntegrationTest`(H2 profile 无需外部):
  - **本地起 Netty ServerSocket 打桩** · 收到 XML 请求回固定 XML 应答 · Green
  - 三种 framing 各测一遍 · Green
  - **UTF-8 + GBK 双编码各测一遍**(GBK 用带中文报文 · verify 编解码回环无乱码)
  - 连接超时 · SocketConnectException 抛
  - 读超时 · SocketTimeoutException 抛
  - 服务端主动断开 · 处理
  - `connectionMode=long` 抛 BusinessException
- [ ] `mvn test` 全绿
- [ ] **Commit**：`feat(sock): SocketClient 短连接 + 三分帧 + 双编码(v0.3.0 · SOCK-1 · #4)`

### Task 5 · interface_config config_json socket 段 + 迁移 SQL

- [ ] `db/migration-v0.3.0-socket.sql`:
  - `ALTER TABLE interface_config MODIFY COLUMN type VARCHAR(16) NOT NULL COMMENT 'SELECT/INSERT/UPDATE/DELETE/SOCKET'`(枚举扩展 · 若已是 VARCHAR 无变更)
  - 幂等注释 · 老客户零破坏
- [ ] `db/init.sql` 同步注释更新
- [ ] `SocketExecRequest` DTO:反解 config_json.socket 段
- [ ] 单元测试 `SocketExecRequestParseTest`:
  - config_json.socket 完整字段解析
  - 分帧字段缺省 → xml_boundary(Q5=A)
  - 编码字段缺省 → UTF-8
  - 超时字段缺省 → connect=3000/read=10000
- [ ] **Commit**：`feat(sock): interface_config SOCKET 类型扩展 + 迁移 SQL(v0.3.0 · SOCK-1 · #5)`

### Task 6 · SocketExecutor 编排 + ExecController 分发

- [ ] `SocketExecutor.execute(InterfaceConfig config, Map<String,Object> params)`:
  1. 解析 config_json.socket 段 · 得 SocketExecRequest
  2. requestTemplate 用 params 渲染(项目已有 `StringUtils.replaceTemplate` 或类似 · verify)
  3. `SocketClient.send()` 得 rawXml 应答
  4. `FormatConverter.parseXml(rawXml)` 得嵌套 Map
  5. `FormatConverter.flattenMap(map, prefix=responseFlattenPrefix)` 得扁平 Map
  6. 返回 SocketExecResponse
- [ ] `ExecController.dispatchByType()` L84-107 加 `case "SOCKET": return socketExecutor.execute(config, params);` 分支
- [ ] `AuditContextHolder.set(new AuditContext().setOpType("SOCKET_EXEC").setTargetDb(config.getSocketIp() + ":" + config.getSocketPort()))`(**verify AuditContext DTO 是否需扩字段**)
- [ ] 集成测试 `SocketExecutorIntegrationTest`:
  - 本地 Netty Mock 起 · 配一个 interface_config 记录 type=SOCKET · POST `/api/exec/{id}` · 拿到扁平化 JSON 应答
  - Mock 服务器发送 host 报文样本(FunctionId=181345 简化版)· 断言扁平化结果字段完整
- [ ] `mvn test` 全绿
- [ ] **Commit**：`feat(sock): SocketExecutor + ExecController 分发(v0.3.0 · SOCK-1 · #6)`

### Task 7 · Eureka 自注册占位注释(Q8=A · 延到 v0.3.2 SOCK-5-B)

- [ ] `EurekaRegistryClient.selfRegister` L55-60 更新 TODO 注释 → 明确"归 v0.3.2 SOCK-5-B" · 附任务计划链接
- [ ] 若 v0.3.0 需 discover 场景(SOCK-1 出站不需要 · 消费方走 host IP:port),不动 discover 代码
- [ ] **无 commit**(仅注释微调 · 随 Task 5/6 一起 commit)

### Task 8 · SOCK-3 MessageDebug 扁平化预览 tab(Q10=A · 归 v0.3.0)

- [ ] `MessageDebug.vue` 加"扁平化"tab · 输入 XML → 调 `POST /api/tools/xml-flatten` 得扁平 JSON · 输出到 monaco editor
- [ ] 后端 `MessageDebugController` 新增 `POST /api/tools/xml-flatten`(免登)· 内部复用 FormatConverter.parseXml + flattenMap
- [ ] vitest 测试 `MessageDebug.test.js` 加扁平化 tab 用例
- [ ] MT-20-* 手工用例同批加"MessageDebug 扁平化预览"1 条(Q22=A)
- [ ] **Commit**：`feat(sock): MessageDebug 扁平化预览 tab + MT 用例(v0.3.0 · SOCK-3 · #8)`

### Task 9 · SOCK-2 InterfaceWizard SOCKET 目标步骤

- [ ] `SocketConfigStep.vue`:
  - 表单 el-form:IP + 端口 + framing 下拉(**必填** · xml_boundary / length_prefix_be4 / length_prefix_be8)+ charset 下拉(**必填** · UTF-8 / GBK)+ connTimeoutMs + readTimeoutMs + connectionMode 下拉(short 唯一可选 · 其他灰置)+ 请求 XML 模板 textarea(monaco) + responseFlattenPrefix input
  - "测试连接"按钮:调 `POST /api/socket/test-connect`(免登)· 报告连通性
- [ ] `InterfaceWizard.vue` 目标类型下拉 el-select 加 `{label: 'SOCKET', value: 'SOCKET'}` · 动态挂 `<SocketConfigStep v-if="form.type === 'SOCKET'">`
- [ ] 后端 `SocketController.testConnect(String ip, int port, int connTimeoutMs): Result<Boolean>`
- [ ] vitest `SocketConfigStep.test.js`:
  - 表单填完能提交 form.socket 段
  - "测试连接"按钮触发 API 调用
  - IP/端口/framing/charset 必填校验
  - connectionMode 只能选 short
- [ ] MT-20-* 手工用例同批加"接口向导 SOCKET 目标类型"3 条(3 分帧各 1 · Q22=A)
- [ ] `npm run build` 通过
- [ ] **Commit**：`feat(sock): InterfaceWizard SOCKET 目标类型步骤 + MT 用例(v0.3.0 · SOCK-2 · #9)`

### Task 10 · SOCK-4 pg-testkit TCP Mock 简化版(Q11=A · 纯 Netty)

- [ ] `SocketMockConfig`:`@ConfigurationProperties(prefix = "socket-mock")` · port + framing + charset + rules(List<SocketMockRule>)
- [ ] `SocketMockRule`:functionId + responseFile
- [ ] `SocketMockServer`:
  - Netty ServerBootstrap · 端口从配置(默认 6500)
  - Pipeline:复用 backend `socket.codec.*`(pg-testkit pom 加 backend 的 Maven 依赖 · verify 循环依赖可能性 · 若有则把 codec 抽到独立 `pg-common` 模块)
  - 收到请求 → dom4j 提取 `<FunctionId>` 值 → 查 rules → 加载 responseFile 内容 → 回写
  - 未匹配 → 回 `<error>FunctionId not found</error>`
- [ ] `pg-testkit/src/main/resources/socket-mock-rules.yml` 示例(Q5=C 三 framing 各示例一条):
  ```yaml
  socket-mock:
    servers:
      - port: 6500
        framing: xml_boundary
        charset: UTF-8
        rules:
          - functionId: "181345"
            responseFile: "mocks/181345-response.xml"
      - port: 6501
        framing: length_prefix_be8
        charset: GBK
        rules:
          - functionId: "180345"
            responseFile: "mocks/180345-response.xml"
  ```
- [ ] `pg-testkit/src/main/resources/mocks/181345-response.xml`:整段 host 报文样本应答
- [ ] `pg-testkit/mvn compile` 通过 · `mvn test` 全绿(单元测试 rule 加载与查找 · 三 framing 各 1 用例)
- [ ] MT-20-* 手工用例同批加"pg-testkit SOCK Mock 启用 + 三 framing 各测"3 条(Q22=A)
- [ ] **Commit**：`feat(testkit): SOCK-4 TCP Mock 简化版 + MT 用例(v0.3.0 · SOCK-4 · #10)`

### Task 11 · 端到端验收(host 报文样本)

- [ ] 本地起 pg-testkit(端口 6500 · 加载 181345 rule)
- [ ] 本地起 backend(端口 8080)
- [ ] Postman/curl 建一个 interface_config 记录:
  - type=SOCKET · config_json 里配 socket 段(ip=localhost port=6500 · framing=xml_boundary · charset=UTF-8 · requestTemplate=host 样本请求 XML · responseFlattenPrefix="")
  - status=published
- [ ] 调 `POST /api/exec/{id}` · body 为空对象(或按 requestTemplate 变量填 params)
- [ ] 断言:响应扁平化 JSON 与 host 日志 `packAnswer` 结果字段级一致(见 模拟host.md 尾部)
- [ ] Screenshot 保存 + 附录到 CHG-037/038 归档
- [ ] **不 commit**(手工验证记录)

### Task 12 · host 使用指南文档(Q12=B · 替代内置 demo)

- [ ] 新建 `docs/05-制品发布/SOCK-lcpt-host-替代指南.md`:
  - 部署后如何用 PG 配一个 FunctionId=181345 接口(截图 + step-by-step)
  - 引用 `docs/04-测试/模拟host.md` 报文样本
  - 引用 pg-testkit 起 mock 命令
  - 客户演示脚本(5 分钟版)
- [ ] MT-20-* 用例汇总:各 Task 已同步补齐 · 本 Task 只补"整合端到端 P1 冒烟"1-2 条(Task 11 手工端到端 + 备用 P1 场景)
- [ ] **Commit**：`docs(sock): host 使用指南 + P1 冒烟用例(v0.3.0 · #12)`

### Task 13 · CHG 归档 + tag 前收尾

- [ ] `docs/03-开发/变更记录.md` 追加 CHG-037(SOCK-1 后端出站)+ CHG-038(SOCK-2/3/4 前端 + Mock)
- [ ] `docs/06-项目管理/路线图.md` "已发布"表加 v0.3.0 行 · "规划中" v0.3.0 段迁"已发布"
- [ ] `docs/06-项目管理/待办与缺陷池.md` § v0.3.0 勾掉 SOCK-1~4 条目
- [ ] `docs/06-项目管理/反馈簿.md` FB-052 状态回写 → 部分已交付(host 出站)· 待 v0.3.2 lcpt-bank 入站
- [ ] `docs/06-项目管理/基线/v0.3.0-基线.md` 新建 · 参考 v0.2.0-基线.md 格式
- [ ] `backend/CLAUDE.md` 已完成单元表加 SOCK-1
- [ ] 后端 `mvn test` 全绿(555+ 用例) · 前端 `npm run build` 通过 · pg-testkit `mvn compile` 通过
- [ ] 手工过 P0 冒烟 10 条 + MT-20-* SOCK 出站用例(路线图 § 打 tag checklist)
- [ ] 3 处顶层索引同步(docs/README.md + 06-项目管理/README.md + 03-开发/任务计划/README.md · CLAUDE.md 规约)
- [ ] `git tag -a v0.3.0 -m "PowerGateway v0.3.0 · lcpt-host 出站场景闭环(SOCK-1~4)"`
- [ ] **push 待用户确认**(memory `feedback_patch_channel_test_driven`)
- [ ] **Commit**：`docs(release): v0.3.0 CHG + 基线 + 索引同步(#13)`

---

## Test Strategy

**单元测试(纯 Java · 无 Spring)**：
- `XmlBoundaryCodecTest` · `LengthPrefixCodecTest` · `SocketClientTest`(空骨架)· `SocketExecRequestParseTest`

**集成测试(H2 profile · 需 `@ActiveProfiles("test")`)**：
- `SocketClientIntegrationTest`:本地 Netty 打桩 · 断言 send/recv
- `SocketExecutorIntegrationTest`:mock interface_config · 走 ExecController 完整链路

**前端 vitest**：
- `SocketConfigStep.test.js`:表单填写 + 测试连接按钮 · 提交结构

**手工测试**：
- Task 11 端到端 host 报文 181345
- Task 12 MT-20-* 用例(**归属待 Q22**)

**验收门槛**：
- 后端 `mvn test` ≥ 600 用例全绿(v0.2.5 基线 598 + SOCK 新增 ~20-25)
- 前端 `npm run build` 通过 · vitest ≥ 55 用例全绿(v0.2.5 基线 54 + SocketConfigStep)
- pg-testkit `mvn compile` 通过
- Task 11 端到端 host 报文 181345 断言字段级一致 · Screenshot 附录

## 依赖与阻塞项

**开工阻塞**：无 · 24 项决策已全部拍板(2026-08-02)

**可并行的独立 Task**：
- Task 2 + Task 3(两个 Codec 独立)
- Task 9 前端(SocketConfigStep) · Task 10 pg-testkit(两个模块解耦)
- Task 4 双编码测试 · 需与 CharsetSupport(Task 1)同期

**依赖关系**：
- v0.2.5 tag 打完再开工 v0.3.0(Q2=A)· 见路线图
- v0.3.0 tag 完成即可开工 v0.3.1(Q1=B)· v0.3.1 前置 trace_id 后 v0.3.0 需回补 SOCK 日志加 traceId(小改)

## 预估工时(按 Q5=C + Q6=B 拍板)

| 阶段 | Task | 人日 |
|---|---|---|
| Netty 依赖 + 骨架 + 枚举 | Task 1 | 1 |
| 分帧编解码 (3 种) | Task 2-3 | 3 |
| SocketClient 实装 + 短连接 + 双编码 | Task 4 | 2.5 |
| InterfaceConfig socket 段 + 迁移 | Task 5 | 1 |
| SocketExecutor + ExecController 分发 | Task 6 | 2 |
| Eureka 占位注释(Q8=A · 不实装) | Task 7 | 0(附随 Task) |
| MessageDebug 扁平化 tab(Q10=A) | Task 8 | 1 |
| 前端 SOCKET 步骤(3 framing + 2 charset) | Task 9 | 3 |
| pg-testkit Mock(Q11=A · 三 framing 各示例) | Task 10 | 3 |
| 端到端 + host 使用指南 + CHG | Task 11-13 | 2.5 |
| **合计** | — | **19-21 人日 ≈ 3 周**(Q5=C + Q6=B 比 A/A 各 +0.5-1 天 · 但都是必要能力) |

## 关键风险

1. **XmlBoundaryCodec 边界识别**:host 报文 `<?xml` 声明有变体(`<?xml  encoding="UTF-8" version="1.0" ?>` 属性顺序反 + 双空格)· dom4j 应能容错,联调 verify
2. **Netty EventLoopGroup 生命周期**:SOCKET 每次执行都新建 group 会导致线程泄露 · Task 4 关注共享 group(project-wide 单例)
3. **XML 字符编码**:GBK 若 Q6=A 延后,后续加时 CharsetConverter 复用有阻力
4. **AuditLog opType 扩展**:`opType="SOCKET_EXEC"` 是否需要 sys_config 白名单 · verify
5. **Eureka 依赖冲突**:REG-1 现有的 eureka-client 版本与 SocketClient 使用的 netty-all 版本是否冲突(Netty 4.x 与 Ribbon/Eureka 通常兼容,但需 verify)

## 相关文档

- Spec 源(上方"Spec 源"段)
- v0.3.2 后续任务计划:[2026-07-28-FB-052-part2-lcpt-bank-inbound.md](./2026-07-28-FB-052-part2-lcpt-bank-inbound.md)
- 待确认清单:[待确认问题清单.md](../../06-项目管理/待确认问题清单.md)
- 后端跨单元复用规约:[backend/CLAUDE.md § 关键代码地标](../../../backend/CLAUDE.md)
