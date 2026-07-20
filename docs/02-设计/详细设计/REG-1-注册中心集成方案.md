# REG-1 · 注册中心集成方案（Nacos + Eureka）

> 文档日期：2026-07-21  
> 作者：Claude Opus 4.7  
> 状态：**方案草案，等用户确认后落地**  
> 用户原文：
> - 系统能同时注册到多个注册中心
> - 我方注册为 `POWERGATEWAY`，供其他系统按名称发现
> - 消费方只填系统名称，不填地址
> - 先支持 **Nacos + Eureka**

---

## 一、目标

PowerGateway 从"配置文件写死目标地址"升级为"按注册中心动态发现"，双向：

```
  外部系统 ──调用──> [注册中心] ──发现──> PowerGateway   （我方作为 Provider）
                                                  ↓
  PowerGateway ──调用──> [注册中心] ──发现──> 外部系统   （我方作为 Consumer）
```

**用户价值**：
1. 无需给客户配置对端 IP:Port，客户只填服务名
2. 支持多注册中心并存（客户环境已有 Nacos，另一个部门用 Eureka，两个都注册）
3. 一台 PG 挂了自动摘除，业务无感知

---

## 二、支持的注册中心

| 注册中心 | 优点 | 缺点 | 版本兼容 |
|---------|------|------|---------|
| **Nacos** | 国内生态最好、支持配置中心、控制台友好 | 依赖较重（自带 MySQL/Derby） | 2.0.x / 2.2.x |
| **Eureka** | Netflix 成熟、轻量、纯服务发现 | 生态偏冷、社区不活跃、只支持 AP 模型 | 1.10.x（Spring Cloud Netflix 3.1.x） |

**推荐**：Nacos 为主推，Eureka 作为兼容适配。

---

## 三、架构设计

### 3.1 抽象层：`RegistryClient` 接口

不直接耦合 Nacos/Eureka SDK，抽出统一接口：

```java
public interface RegistryClient {
    String getName();                                       // "nacos" / "eureka"
    boolean isConfigured();                                 // 该注册中心是否配置了地址
    void register(ServiceInstance self);                    // 注册自己
    void deregister();                                      // 注销自己
    List<ServiceInstance> discover(String serviceName);     // 按服务名发现实例
    void heartbeat();                                       // 心跳（由调度线程调用）
}
```

实现类：`NacosRegistryClient` + `EurekaRegistryClient`。

### 3.2 门面：`RegistryFacade`

对业务代码只暴露一个 `RegistryFacade`，业务不感知具体注册中心：

```java
@Service
public class RegistryFacade {
    private final List<RegistryClient> enabledClients;  // 由 RegistryConfig 注入所有已启用的

    // 消费方：按服务名找一个可用实例（跨所有注册中心聚合）
    public ServiceInstance choose(String serviceName) { ... }

    // 提供方：注册到所有已配置的注册中心
    public void registerSelf() { enabledClients.forEach(c -> c.register(selfInstance)); }
}
```

### 3.3 与现有 `port_route` 表的兼容

现有 `port_route.port_address` 存的是硬编码 URL（如 `http://192.168.1.100:8080/api/xxx`）。

**扩展**：`port_address` 支持两种格式，运行时自动判别：

| 格式 | 含义 | 示例 |
|------|------|------|
| 完整 URL | 直连（现状） | `http://192.168.1.100:8080/api/query` |
| `service://` 协议 | 注册中心发现 | `service://CBS_SYSTEM/api/query` |

`DispatchService` 转发前解析 URL：
- 完整 URL → 直接用
- `service://` → 用 `RegistryFacade.choose("CBS_SYSTEM")` 拿实例，拼成完整 URL

**好处**：老配置不需要迁移，新配置用新协议。

---

## 四、配置模型

### 4.1 数据库表：`registry_config`（新增）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 主键 |
| type | varchar(32) | `nacos` / `eureka` |
| name | varchar(64) | 用户起的别名，如"内部Nacos" |
| server_addr | varchar(512) | Nacos: `host:port` 逗号分隔；Eureka: `http://host:port/eureka/` |
| namespace | varchar(64) | Nacos 命名空间（可空） |
| group_name | varchar(64) | Nacos 分组（默认 DEFAULT_GROUP） |
| username | varchar(64) | 可空 |
| password | varchar(255) | AES 加密，可空 |
| enabled | tinyint | 0/1 是否启用 |
| register_self | tinyint | 是否把 PG 自身注册进去（0/1） |
| service_name | varchar(64) | 自注册用的服务名，默认 `POWERGATEWAY` |
| extra_metadata | text | JSON，注册时携带的额外元数据 |
| created_at / updated_at | datetime | |

**逻辑约束**：多个注册中心可同时启用；`service_name` 可以在不同注册中心用不同名字。

### 4.2 系统配置菜单增强

在「系统管理 → 系统配置」加一个 Tab「注册中心」，或独立菜单「系统管理 → 注册中心管理」：

- 列表：显示所有已配置的注册中心，状态灯（已连接/断开/未启用）
- 新增：type / name / 地址 / 认证 / 启用开关 / 是否自注册 / 服务名
- 编辑 / 删除 / 测试连通
- **服务发现预览**：给一个服务名，看能从哪些注册中心发现到实例

---

## 五、消费方（PG 调对外系统）改造

### 5.1 端口路由的目标地址填法

`port_route.port_address`：

| 场景 | 填法 |
|------|------|
| 老配置（直连） | `http://192.168.1.100:8080/api/query` |
| 通过注册中心 | `service://CBS_SYSTEM/api/query` |
| 指定协议 | `service://CBS_SYSTEM/api/query?scheme=https` |

前端「端口路由配置」页新增：
- 目标地址输入框旁边加一个"选择服务"按钮
- 弹窗列出所有注册中心里可发现的服务（下拉），选完自动填 `service://xxx/`
- 路径自己填

### 5.2 负载均衡

初版：轮询（Round Robin）。
后续：权重（读注册中心元数据里的 `weight`）。

### 5.3 失败降级

- 服务发现失败（注册中心宕机）→ 用本地缓存的实例列表（缓存 30s）
- 缓存也没有 → 抛 `RegistryDiscoveryException`，业务返回统一错误

---

## 六、提供方（PG 被外部调用）改造

### 6.1 自注册的时机

- Spring `ApplicationReadyEvent` 触发 → 遍历 `registry_config` 里 `enabled=1 && register_self=1` 的配置，逐个注册
- Spring `ContextClosedEvent` → 反向注销

### 6.2 自注册的元数据

```json
{
  "service_name": "POWERGATEWAY",
  "ip": "本机 IP（自动探测，可覆盖）",
  "port": 8080,
  "metadata": {
    "version": "0.1.0",
    "product": "PowerGateway",
    "interfaces": ["QUERY_ACCOUNT", "TRANSFER"]  // 已发布的接口列表（可选）
  }
}
```

### 6.3 健康检查

- Nacos：定时心跳（5s 一次），超过 15s 无心跳标为不健康，30s 摘除
- Eureka：默认 30s 心跳，90s 摘除

Spring 内部起一个 `@Scheduled` 任务统一调 `RegistryFacade.heartbeatAll()`。

---

## 七、依赖影响

### 7.1 新增依赖

| 依赖 | 大小 | 备注 |
|------|------|------|
| `nacos-client` | ~3MB | 官方 SDK，2.2.x |
| `eureka-client`（可选） | ~1MB | Spring Cloud Netflix Eureka |

**做成 profile 可裁剪**：
- `--registry-none` → 不打包注册中心依赖（默认，兼容老用户）
- `--registry-nacos` → 只带 Nacos
- `--registry-full` → Nacos + Eureka 全带

（对应 G 组打包方案的裁剪策略）

### 7.2 与现有代码的兼容

**零侵入**：
- `RegistryFacade` bean 在无任何 `registry_config` 时为空实现，`choose()` 抛 `NoRegistryConfiguredException`
- `DispatchService` 遇到 `service://` URL 时才调 `choose()`
- 老 `port_route` 全部是完整 URL，走原路径，无任何影响

---

## 八、UI 设计草图

### 8.1 「注册中心管理」列表页

```
┌────────────────────────────────────────────────────────────┐
│  + 新增注册中心                                              │
├────────────────────────────────────────────────────────────┤
│  状态  类型    名称        地址              自注册  操作    │
│  🟢   Nacos   内部Nacos   192.168.1.10:8848  是    编辑 删除 │
│  🟢   Eureka  部门Eureka  http://.../eureka  否    编辑 删除 │
│  🔴   Nacos   备用Nacos   ...                是    编辑 删除 │
└────────────────────────────────────────────────────────────┘

[测试连通]  [服务发现预览]
```

### 8.2 「端口路由」目标地址填写

```
目标地址：[ service://CBS_SYSTEM/api/query          ]  [选择服务▼]

服务列表（弹窗）：
  内部Nacos:
    · CBS_SYSTEM   (3 实例)
    · CRM_SYSTEM   (2 实例)
  部门Eureka:
    · LEGACY_HOST  (1 实例)
```

---

## 九、测试计划

| 测试类型 | 内容 |
|---------|------|
| 单元测试 | `NacosRegistryClient` / `EurekaRegistryClient` 增删注册、发现、心跳 |
| 集成测试 | 启动内嵌 Nacos（testcontainers），验证 PG 注册和发现 |
| 端到端 | pg-testkit 里配置一个 Mock 服务注册到 Nacos，PG 通过 `service://` 调用它 |
| 降级测试 | 停 Nacos，验证 30s 缓存内可用；过期后抛错 |

---

## 十、待用户确认的决策点

| # | 决策点 | 我的建议 |
|---|--------|---------|
| 1 | 是否也接管"配置中心"能力（从 Nacos 读 sys_config）？ | **暂不做**。sys_config 现在已有热更新机制，加配置中心是大改，收益不高 |
| 2 | 是否支持"启动即注册"vs"手动触发注册" | 启动即注册（默认），管理界面提供"重新注册"按钮兜底 |
| 3 | 是否支持"接口维度"注册（每个已发布接口在 Nacos 注册为独立服务）？ | **不做**。用元数据 `metadata.interfaces` 列出即可，一个 PG 实例就是一个服务 |
| 4 | 服务名 `POWERGATEWAY` 是全局固定还是每台机器可改？ | 每台机器可改（多个 PG 集群时避免同名） |
| 5 | 支持 Nacos 分组和命名空间隔离？ | 支持，配置项里给字段 |
| 6 | 心跳失败是否触发告警到运维（对接 SYS-2）？ | **是**，接入 perf_alert |

---

## 十一、工时估算

| 阶段 | 工时 |
|------|------|
| 抽象层 `RegistryClient` + `RegistryFacade` + 配置表 | 1 天 |
| Nacos 实现 + 单测 | 1.5 天 |
| Eureka 实现 + 单测 | 1 天 |
| `port_route` 兼容 `service://` 协议 + Dispatch 改造 | 1 天 |
| 前端「注册中心管理」页 | 1 天 |
| 前端「端口路由」选服务弹窗 | 0.5 天 |
| 集成测试（testcontainers Nacos） | 1 天 |
| **合计** | **~7 天** |

---

## 十二、依赖 G 组打包方案的部分

- 打包时对 `nacos-client` / `eureka-client` 做 profile 裁剪
- Windows 无 Nacos 环境的用户默认不带这两个依赖，需要时通过 `--registry-nacos` 拉起

见 `docs/03-开发/打包发布方案.md`。
