# M2-7 接口发布 + Swagger 集成 设计文档

**日期**：2026-05-05  
**状态**：已确认  
**前置依赖**：M2-3、M2-4、M2-5、M2-6

---

## 范围

接口状态管理（草稿/发布/禁用）+ 对外统一执行入口 `/api/exec/{id}` + Swagger 动态文档同步 + 前端接口列表页。

**不含**：接口配置本身（M2-3～M2-6）、分库分表路由（M2-8）、缓存（M2-10）。

---

## 后端设计

### 1. 状态流转

```
draft ──publish──▶ published ──disable──▶ disabled
  ▲                                           │
  └─────────────────────────────────────────-─┘（re-publish）
```

- `publish(Long id)`：`status → published`，写入 `path = /api/exec/{id}`
- `disable(Long id)`：`status → disabled`（draft/published 均可转入）
- `delete(Long id)`：**后端同时校验**：status=published 时拒绝删除，返回 400"请先禁用后再删除"

### 2. 新建 ExecController

```
POST /api/exec/{interfaceId}
Content-Type: application/json
Body: {
  "params": { "key": "value", ... },
  "page": 1,          // 仅 SELECT 生效，可选
  "pageSize": 20      // 仅 SELECT 生效，可选
}
```

**鉴权**：在 `SaTokenConfig` 排除列表加入 `/api/exec/**`，对外完全开放。

**状态校验**：
- `status = disabled` → `{"code": 403, "message": "接口已禁用"}`
- `status = draft`    → `{"code": 400, "message": "接口未发布"}`

**类型分发**：
| type   | 调用方法                                      | 返回                  |
|--------|-----------------------------------------------|-----------------------|
| SELECT | `executeQuery(id, params, page, pageSize)`    | `List<Map>`           |
| INSERT | `executeInsert(id, params)`                   | 影响行数 `Integer`    |
| UPDATE | `executeUpdate(id, params)`                   | 影响行数 `Integer`    |
| DELETE | `executeDelete(id, params)`                   | 影响行数 `Integer`    |

**审计上下文**：UPDATE/DELETE 分发前需设置 `AuditContextHolder`（与现有 `InterfaceConfigController.execute()` 一致），否则 M2-9 审计日志不会写入。

### 3. executeQuery 新增全量版本

在 `InterfaceConfigService` 新增：

```java
public List<Map<String, Object>> executeQuery(Long id, Map<String, Object> params,
                                               Integer page, Integer pageSize)
```

- `page`/`pageSize` 均不为 null 时：SQL 追加 `LIMIT pageSize OFFSET (page-1)*pageSize`
- 任意一个为 null 时：全量返回，不加 LIMIT
- 内部复用已有的 `QueryBuilder.build()` + `executeQuery(conn, sqlResult)`

### 4. Swagger 动态注册

实现 `OpenApiCustomizer` Bean（`config/OpenApiDynamicCustomizer.java`）：

- SpringDoc 每次请求 `/v3/api-docs` 时自动调用
- 查询所有 `status = published` 的接口配置
- 为每条接口动态注入 `PathItem`（路径 `/api/exec/{id}`，POST 方法，含接口名、类型说明）
- 请求体 Schema：`params`（object）+ `page`（integer，SELECT 时）+ `pageSize`（integer，SELECT 时）

---

## 前端设计

### 页面：`views/interface/InterfaceList.vue`

**列表字段**：

| 列     | 展示方式                                              |
|--------|-------------------------------------------------------|
| 接口名称 | 纯文本                                               |
| 类型   | `el-tag`：SELECT=蓝、INSERT=绿、UPDATE=橙、DELETE=红  |
| 状态   | `el-tag`：draft=灰、published=绿、disabled=红         |
| 访问路径 | published 时显示 `/api/exec/{id}`，否则 `—`          |
| 操作   | 发布 / 禁用 / 编辑 / 删除                             |

**操作逻辑**：
- **发布**：draft 和 disabled 状态显示，调用 `POST /api/interface/{id}/publish`
- **禁用**：published 状态显示，调用 `POST /api/interface/{id}/disable`
- **编辑**：按 type 跳转对应配置页，携带 `?id={id}` 参数
  - SELECT → `/interface/dev`
  - INSERT → `/interface/insert`
  - UPDATE → `/interface/update`
  - DELETE → `/interface/delete`
- **删除**：`el-popconfirm` 二次确认；已 published 状态不允许删除，提示"请先禁用后再删除"

**路由**：在 `src/router/index.js` 新增：
```js
{
  path: 'interface/list',
  name: 'InterfaceList',
  component: () => import('@/views/interface/InterfaceList.vue'),
  meta: { title: '接口管理' }
}
```

### API 扩展：`src/api/interface.js`

新增方法：
- `publishInterface(id)`：`POST /api/interface/{id}/publish`
- `disableInterface(id)`：`POST /api/interface/{id}/disable`

---

## 验收标准

1. 调用 `POST /api/interface/{id}/publish` 后，status=published，path 字段写入正确
2. 发布后通过 `POST /api/exec/{id}` 可成功调用四种类型接口，无需携带 token
3. SELECT 接口传 page/pageSize 时返回正确分页，不传时全量返回
4. disabled 接口调用 `/api/exec/{id}` 返回 403
5. draft 接口调用 `/api/exec/{id}` 返回 400
6. Swagger UI (`/swagger-ui.html`) 中能看到已发布接口的动态文档条目
7. 前端接口列表展示正确，发布/禁用按钮按状态动态切换
8. 已发布接口在前端不允许直接删除

---

## TDD 测试计划

| 测试类 | 覆盖场景 |
|--------|---------|
| `M27PublishTest`（Service 层）| publish/disable 状态流转；重复发布幂等；draft 接口执行返回400 |
| `M27ExecControllerTest`（MockMvc） | SELECT/INSERT/UPDATE/DELETE 分发；disabled 返回403；无 token 可访问 |
| `M27QueryPaginationTest`（工具类） | 有分页参数时 SQL 含 LIMIT；无分页参数时无 LIMIT |
