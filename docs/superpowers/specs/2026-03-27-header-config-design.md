# 设计文档：渠道级别报文头适配配置

- **日期**：2026-03-27
- **关联问题**：问题清单 · 问题2
- **影响单元**：M1-4（渠道模板管理）、M1-7（端口分发路由）
- **变更编号**：CHG-002

---

## 背景

`PortRouteService.forwardWithRetry()` 中 HTTP 请求头硬编码为 `Content-Type: text/plain`，无法适配目标 B 系统对不同 Content-Type（如 `application/json`、`application/xml`）或字符编码（如 GBK）的要求。需要在渠道级别和端口路由级别分别提供配置，并支持真正的字节级编码转换。

---

## 目标

1. 渠道级别（`channel_config`）可配置默认报文头
2. 端口路由级别（`port_route`）可覆盖渠道默认值
3. 支持 Content-Type、Charset（含真实转码）、任意自定义 KV Header
4. 覆盖双向：出向请求头（A→B）和返回响应头（B→A）

---

## 数据模型

### `HeaderConfig`（Java DTO，不新增数据库表）

```java
public class HeaderConfig {
    /** 出向请求的 Content-Type，e.g. "application/json", "application/xml", "text/plain" */
    private String contentType;

    /** 出向请求的字符集，e.g. "UTF-8", "GBK", "ISO-8859-1"；null 或空 = 不转码 */
    private String charset;

    /** 出向请求自定义 Header（A→B），key=header 名，value=header 值 */
    private Map<String, String> requestHeaders;

    /** 返回给 A 的响应头，key=header 名，value=header 值 */
    private Map<String, String> responseHeaders;
}
```

### 数据库变更

```sql
-- channel_config 新增列
ALTER TABLE channel_config
  ADD COLUMN header_config TEXT NULL
  COMMENT '渠道级别报文头配置（JSON），字段见 HeaderConfig';

-- port_route 新增列
ALTER TABLE port_route
  ADD COLUMN header_config TEXT NULL
  COMMENT '端口路由级别报文头配置（JSON），覆盖渠道默认值';
```

### 两级合并规则

| 字段 | 合并策略 |
|------|---------|
| `contentType` | port_route 非空则覆盖，否则取 channel_config 值 |
| `charset` | port_route 非空则覆盖，否则取 channel_config 值 |
| `requestHeaders` | 先取 channel_config 的 map，再逐键 putAll port_route 的 map |
| `responseHeaders` | 同上 |

---

## 服务层设计

### 新增 `HeaderConfigMerger`（工具类）

```
职责：接收 channel 级 HeaderConfig + route 级 HeaderConfig，输出合并后的 HeaderConfig
依赖：无（纯 Java，可独立单测）
```

### 新增 `CharsetConverter`（工具类）

```
职责：字节级编码转换
  encode(String text, String fromCharset, String toCharset) → byte[]
  decode(byte[] bytes, String charset) → String
支持：UTF-8 / GBK / ISO-8859-1
依赖：无（Java 标准库 Charset，可独立单测）
```

### `PortRouteService.dispatch()` 执行链变更

```
原链路：
  渠道路由 → 请求转换（可选）→ forwardWithRetry → 应答转换（可选）→ 返回

新链路：
  渠道路由
  → 查 port_route（含 header_config）
  → 查 channel_config（含 header_config）
  → HeaderConfigMerger.merge(channel, route) → effectiveHeader
  → 请求转换（可选）
  → 若 charset 非 UTF-8：CharsetConverter.encode(requestMsg)
  → 构造 HttpHeaders（contentType, charset, requestHeaders）
  → forwardWithRetry（使用新 HttpHeaders）
  → 若 charset 非 UTF-8：CharsetConverter.decode(bResponse)
  → 应答转换（可选）
  → 构造返回 HttpHeaders（responseHeaders）
  → 返回给 A
```

### `ChannelConfigService` 变更

- `save()` 接收 `headerConfig`（`HeaderConfig` 对象），序列化为 JSON 存入 `header_config` 列
- `getByCode()` 返回时反序列化 `header_config` 列填充 `ChannelConfig.headerConfig`

### `PortRouteService` 变更

- `saveRoute()` 同上，序列化 `headerConfig` 存入 `port_route.header_config`
- Redis 缓存中 `PortRoute` 对象已含 `headerConfig`，无需额外改动

---

## 前端设计

### 渠道模板管理（`ChannelConfig.vue`）

新增/编辑弹窗底部增加"报文头配置"折叠区（`el-collapse`）：

| 控件 | 说明 |
|------|------|
| Content-Type 选择器 | `el-select`，选项：`application/json` / `application/xml` / `text/plain` / 自定义输入 |
| Charset 选择器 | `el-select`，选项：`UTF-8`（默认）/ `GBK` / `ISO-8859-1` |
| 请求头 KV 表格 | 可增删行，每行两个 `el-input`（Header 名 / Header 值） |
| 响应头 KV 表格 | 同上 |

### 端口分发路由（`PortRoute.vue`）

新增/编辑弹窗增加同样的"报文头配置"折叠区，并在表单上方显示提示：
> "未填写的项将继承渠道默认值"

---

## 测试策略

| 测试类 | 类型 | 覆盖点 |
|--------|------|--------|
| `HeaderConfigMergerTest` | 纯单元测试 | 两级合并各字段优先级、null 处理、map 合并 |
| `CharsetConverterTest` | 纯单元测试 | UTF-8→GBK→UTF-8 往返无损；不支持编码报错 |
| `M14ChannelHeaderTest` | SpringBootTest + H2 | 保存含 header_config 的渠道，查询后正确反序列化 |
| `M17HeaderDispatchTest` | SpringBootTest + MockRestServiceServer | dispatch 时请求头正确注入；charset 转码正确 |

---

## 验收标准

1. 渠道配置页面可保存/编辑 Header 配置，刷新后数据不丢失
2. 端口路由配置页面可保存/编辑 Header 配置，未填写时显示"继承渠道默认值"
3. dispatch 转发时，B 系统收到的请求 Content-Type 与配置一致
4. 配置 charset=GBK 时，转发报文已转为 GBK 编码；B 系统 GBK 应答正确还原为 UTF-8
5. 端口路由的 Header 配置覆盖渠道默认值（同名 key 以路由配置为准）
6. 所有测试通过，`mvn test` 无失败

---

## 不含（本次范围外）

- 渠道级别的 Header 预览/测试功能（不在本次范围）
- 入向请求（A 发来的请求）的 Header 校验（仅处理出向和返回，不做入向拦截）
- Header 加密存储（当前 KV 明文存储，认证 Token 类 Header 暂不加密）
