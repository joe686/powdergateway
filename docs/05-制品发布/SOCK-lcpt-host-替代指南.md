# SOCK-lcpt-host 替代指南(v0.3.0)

> **场景**:金融/银行渠道对接常见"上游 JSON → 中间转换器 → 下游 Socket + XML"链路(用户示例 lcpt-host)。
> PowerGateway v0.3.0 SOCK-1 交付出站 TCP Socket + XML 报文接入完整能力 · 能替代 lcpt-host 做 JSON→XML→Socket 转发中转。
>
> 本指南说明如何部署 PG + 配置一个 SOCKET 接口 · 5 分钟跑通端到端 · 演示替代 lcpt-host。

---

## 一、前置条件

- PowerGateway v0.3.0+ 已部署(backend `http://localhost:8080` · frontend `http://localhost:5173`)
- 目标 Socket 服务端已在跑(或用 pg-testkit 起 mock 演示,见 § 二.可选)
- 管理员账号 · admin / Admin@123

**目标 Socket 服务端信息(从对接方拿)**:

| 项 | 示例值 | 说明 |
|---|---|---|
| IP | `10.1.2.3` | 目标 host |
| Port | `6500` | 目标 TCP 端口 |
| Framing | `xml_boundary` / `length_prefix_be4` / `length_prefix_be8` | 分帧策略(必确认) |
| Charset | `UTF-8` / `GBK` | 编码(必确认) |
| Request XML | `<?xml ... ?><Transaction><FunctionId>...</FunctionId>...</Transaction>` | 请求报文模板 |
| Response XML | `<Response>...<Result>...</Result></Response>` | 应答报文样例 |

参考:[`docs/04-测试/模拟host.md`](../04-测试/模拟host.md) FunctionId=181345 客户货币组合产品查询完整报文。

---

## 二、(可选)启动 pg-testkit SOCK Mock 做本地演示

若无真实目标 Socket · 可用 pg-testkit 内置 mock 演示。

### 1. 修改 `pg-testkit/src/main/resources/application.yml`

```yaml
socket-mock:
  enabled: true         # 打开 mock 服务(默认 false)
  port: 6500
  rules:
    - function-id: "181345"
      response-file: "mocks/181345-response.xml"
```

### 2. 启动 pg-testkit

```bash
cd pg-testkit
mvn spring-boot:run
```

启动日志:
```
SOCK-4 · SocketMockServer 启动:port=6500 rules=1 条
SOCK-4 · rule 加载:functionId=181345 responseFile=mocks/181345-response.xml bytes=xxx
```

> **限制**(v0.3.0):pg-testkit SocketMock 仅支持 `xml_boundary` 分帧 + UTF-8。若需 `length_prefix_be4/be8` 或 GBK · 请用真实目标 Socket 或等 v0.3.7 pg-testkit 增强。

---

## 三、创建 SOCKET 接口配置

### 方式 A(推荐):Swagger UI

1. 打开 `http://localhost:8080/swagger-ui.html`
2. 登录:`POST /api/auth/login` · body `{"username":"admin","password":"Admin@123"}` · 拷贝 `data.token`
3. 顶部 "Authorize" 输入 `token值`(不加 Bearer 前缀 · Sa-Token 走 satoken header)
4. 找 `接口配置管理 → POST /api/interface-config/save` · 填 body:

```json
{
  "name": "host-181345-货币组合产品查询",
  "type": "SOCKET",
  "configJson": "{\"socket\":{\"ip\":\"127.0.0.1\",\"port\":6500,\"framing\":\"xml_boundary\",\"charset\":\"UTF-8\",\"connTimeoutMs\":3000,\"readTimeoutMs\":10000,\"connectionMode\":\"short\",\"requestTemplate\":\"<?xml version=\\\"1.0\\\" encoding=\\\"UTF-8\\\"?><Transaction><FunctionId>181345</FunctionId><CustomerId>{customerId}</CustomerId></Transaction>\",\"responseFlattenPrefix\":\"\"}}"
}
```

拿到返回的 `data.id`(接口 ID)。

5. `POST /api/interface-config/publish/{id}` 发布接口 · status 变 `published`。

### 方式 B:前端 UI(v0.3.7 wizard 深度集成后)

v0.3.0 前端 InterfaceWizard 暂未集成 SOCKET 类型。等待 v0.3.7 前端补齐(见路线图)。当前请用方式 A Swagger。

---

## 四、触发调用

用 curl 或 Postman 调 exec 入口:

```bash
curl -X POST http://localhost:8080/api/exec/{接口ID} \
  -H "Content-Type: application/json" \
  -d '{"params":{"customerId":"C001"}}'
```

### 预期响应

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "rawXml": "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response>...</Response>",
    "flattened": {
      "FunctionId": "181345",
      "Result.Code": "0",
      "Result.Msg": "SUCCESS",
      "Data.CustomerId": "C001",
      "Data.CustomerName": "示例客户",
      "Data.ProductList.Product.ProductCode": "P001",
      ...
    },
    "latencyMs": 12
  }
}
```

`flattened` 是扁平化字段 Map(嵌套 XML 用 `.` 拼接)· 供后续业务系统直接消费。

---

## 五、验证与对齐

对比 lcpt-host 日志中 `packAnswer` 字段级一致:
- `FunctionId`:181345 一致
- `Result.Code / Msg`:一致
- `Data.CustomerId / CustomerName`:一致
- `Data.ProductList.Product.ProductCode / ...`:一致

若不一致 · 常见原因:
1. **分帧错**:重新与对接方确认 framing 类型
2. **编码错**:中文乱码 → 切换 GBK
3. **模板变量未替换**:检查 `requestTemplate` 里 `{paramName}` 是否与 curl body 里 `params` 键一致
4. **超时**:调大 `readTimeoutMs`(默认 10000ms)

---

## 六、客户演示脚本(5 分钟版)

1. **背景**(30s):金融渠道对接常见"上游 JSON → 中间转换器 → 下游 Socket + XML"· PG 就是替代传统中转器(如 lcpt-host)的零编码平台。
2. **启动**(30s):`bash scripts/start.sh` 起 PG · 展示 backend 8080 + frontend 5173 已就位。
3. **配置**(2 分钟):打开 Swagger UI · 一 POST 创建 SOCKET 接口配置 · 一 POST 发布 · 展示 config_json 里 socket 段结构。
4. **触发**(1 分钟):curl POST /api/exec/{id} · 观察响应 rawXml + flattened + latencyMs · 强调"零编码 · 纯配置"。
5. **对齐**(30s):对比原 lcpt-host 日志字段级一致 · 强调"代码 0 行 · 端到端 3 分钟"。
6. **收尾**(30s):说明未来 v0.3.2 lcpt-bank 入站场景(TCP XML 入 → HTTP 出到联机 → 应答回环)· 全链路 traceId。

---

## 七、故障排查

| 现象 | 排查 |
|---|---|
| `SocketConnectException:连接超时` | 目标 host:port 不可达 · 检查网络 / 目标服务未启动 |
| `SocketTimeoutException:读超时` | 目标服务端未按预期应答 · 检查 framing/charset · 或调大 readTimeoutMs |
| `报文解析失败 [XML]` | 应答 XML 格式非法 · 用 MessageDebug 工具"XML 扁平化"tab verify |
| 中文乱码 | charset 配错 · 切换 UTF-8 ↔ GBK 重试 |
| `暂只支持 short 短连接` | connectionMode 配了 long/pooled · v0.3.0 仅实装 short |

---

## 八、相关文档

- [路线图 v0.3.0](../06-项目管理/路线图.md#v030--sock-出站--lcpt-host-场景闭环)
- [反馈簿 FB-052](../06-项目管理/反馈簿.md#fb-052)
- [任务计划 v0.3.0](../03-开发/任务计划/2026-07-28-FB-052-part1-lcpt-host-outbound.md)
- [模拟 host 报文样本](../04-测试/模拟host.md)
- [产品说明书](./产品说明书.md)

---

**v0.3.0 SOCK-1 已交付出站能力 · v0.3.2 SOCK-5 将补入站 lcpt-bank 场景(TCP XML 入 → HTTP 出到联机 → 回环)**。
