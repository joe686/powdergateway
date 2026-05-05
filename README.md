# PowerGateway

低代码/零代码可视化接口开发平台。通过界面配置替代手工编写接口代码，目标是将单个接口的开发工时压缩到 10 分钟以内。

## 功能概览

### 模块一：报文格式转换

| 功能 | 说明 |
|------|------|
| 格式互转 | JSON ↔ XML ↔ CSV ↔ FormData，12 种组合 |
| 字段映射 | 拖拽配置源字段 → 目标字段映射关系 |
| 字段加工 | 截取、补位、大小写、类型转换、四则运算，多规则叠加 |
| 渠道模板 | 按报文中的渠道标识字段自动匹配转换模板 |
| 端口分发 | 双向转发：请求加工 → 转发目标端口 → 应答加工返回，支持 GBK/UTF-8 编码转换 |

### 模块二：可视化接口开发

| 功能 | 说明 |
|------|------|
| 查询接口 | 多表 LEFT JOIN，可视化 WHERE 条件，字段加工 |
| 插入接口 | 多表（最多3张），三种字段来源：请求参数/固定值/运算表达式，事务回滚 |
| 修改接口 | 强制唯一条件校验，修改前快照写入审计日志 |
| 删除接口 | 多表（最多3张），批量删除保护开关，删除前预览待删数据 |
| SQL 审计 | 所有增删改操作异步写入独立审计库，含操作人、IP、执行结果、修改前数据 |

## 技术栈

| 层 | 技术 |
|----|------|
| 后端框架 | Spring Boot 2.7 / JDK 1.8 |
| ORM | MyBatis-Plus 3.5 |
| 认证 | Sa-Token + JWT |
| 缓存 | Redis 6.2（分布式）+ Caffeine（本地双层） |
| 报文解析 | Jackson（JSON）、Dom4j（XML）、OpenCSV（CSV） |
| 前端框架 | Vue 3 + Vite |
| UI | Element Plus |
| 状态管理 | Pinia |
| 图表 | ECharts |
| 支持数据库 | MySQL 8.0+、Oracle 11g+、PostgreSQL 12+ |

## 本地开发

**前置条件**：JDK 1.8+、Maven 3.6+、Node 18+、MySQL 8.0、Redis 6.2

### 数据库初始化

```bash
# 执行配置库建表脚本（约 9 张表）
mysql -u root -p your_db < backend/src/main/resources/db/init.sql
```

### 启动后端

```bash
cd backend
# 修改 src/main/resources/application.yml 中的 MySQL/Redis 连接信息
mvn spring-boot:run
# 访问 http://localhost:8080/swagger-ui.html
```

### 启动前端

```bash
cd frontend
npm install
npm run dev
# 访问 http://localhost:5173
```

默认管理员账号：`admin` / `Admin@123`

### 运行测试

```bash
cd backend
mvn test        # 全量测试，使用 H2 内存库，无需外部 MySQL/Redis
```

## 项目结构

```
powergateway/
├── backend/
│   └── src/main/java/com/powergateway/
│       ├── controller/     REST 接口层
│       ├── service/        业务逻辑层
│       ├── dao/            MyBatis-Plus Mapper
│       ├── model/          实体类 & DTO
│       ├── utils/          SQL 构建器、格式转换、字段加工引擎
│       ├── aop/            SQL 审计 AOP
│       └── config/         Sa-Token、Redis、多数据源配置
└── frontend/
    └── src/
        ├── views/          业务页面（按模块分目录）
        ├── components/     可复用组件（ConditionBuilder 等）
        ├── api/            axios 请求封装（按模块分文件）
        └── store/          Pinia 状态（用户信息、token）
```

## 已完成功能（阶段三）

- **P0**：脚手架、登录鉴权、配置库建表
- **M1**：格式转换引擎、字段映射、字段加工、渠道模板、转换模板 CRUD、报文转换 API、端口分发路由（含双向报文头配置）
- **M2**：数据库连接管理、表结构查询（Redis 缓存）、SQL 审计日志、查询接口配置、插入接口配置、修改接口配置、**删除接口配置（M2-6，当前版本）**

下一步：M2-7 接口发布（状态流转 + 统一执行入口 `/api/exec/{id}`）
