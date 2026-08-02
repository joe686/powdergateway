# pg_demo 接口开发练习（连表 CRUD 手工测试）

> **目标读者**：初次上手 PowerGateway 接口开发的用户，跟着走完能覆盖 **查（多表 JOIN / 聚合 / 窗口）/ 增（多步事务）/ 改（转账）/ 删（级联）** 各类典型场景，同时熟悉表结构 + MySQL 常用查询 + 在 PG 管理台把这些操作配成接口。
> **前置**：pg-testkit 已建好 `pg_demo` 库（见 [连接配置速查 §三·B](./连接配置速查.md#三b样例业务库-pg_demo-demodb--接口开发练手)），后端 8080 / 前端 5173 / pg-testkit 8081 都已启动。
> **文档日期**：2026-07-28（CR-005 · v0.2.5 minor · 7 张占位表补真 DDL + 种子数据后修订）
> **适用版本**：v0.2.5+

---

## 一、MySQL 自查快查命令（先掌握这几条）

在 DBeaver / Navicat / `mysql` CLI 里，用户最常用的元信息查询：

```sql
-- 1. 列出当前 MySQL 实例的所有库
SHOW DATABASES;

-- 2. 列出某个库的所有表（两种等价写法）
SHOW TABLES FROM pg_demo;
SELECT TABLE_NAME
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'pg_demo';

-- 3. 看某张表的完整字段定义 + 索引 + 引擎
SHOW CREATE TABLE pg_demo.demo_user\G     -- CLI 里 \G 竖排；DBeaver 里去掉 \G
DESC pg_demo.demo_user;                   -- 精简版

-- 4. 看某张表有多少行
SELECT COUNT(*) FROM pg_demo.demo_user;

-- 5. 全库各表行数一览（超实用）
SELECT TABLE_NAME, TABLE_ROWS
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'pg_demo'
ORDER BY TABLE_NAME;
-- 注：InnoDB 的 TABLE_ROWS 是估算值，精确值请 COUNT(*)

-- 6. 查外键关系（本项目 demo_* 表没设 FK，但业务库常用）
SELECT TABLE_NAME, COLUMN_NAME, REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = 'pg_demo' AND REFERENCED_TABLE_NAME IS NOT NULL;
```

---

## 二、pg_demo 表结构详解

`pg_demo` 库当前有 **10 张 `demo_*` 表**，由 pg-testkit 的 `MysqlDemoDbInitializer` 通过 `POST /testkit/demo-db/init` 一键建表 + 灌种子。**10 张表全部为真业务结构**（CR-005 之前只有 3 真 + 7 占位）。

### 2.1 账户体系（用户 · 账户 · 交易）

#### `demo_user`——用户主表

记录 C 端用户的基本信息 + 汇总余额。1 用户 → N 账户 / N 交易 / N 订单 / N 地址 / N 操作日志。

| 字段 | 类型 | 含义 |
|---|---|---|
| `id` | BIGINT PK AUTO | 主键 |
| `user_no` | VARCHAR(32) UNIQUE | 用户编号（业务主键，如 U000001）|
| `name` | VARCHAR(64) | 用户姓名 |
| `gender` | TINYINT | 性别（1=男 2=女）|
| `phone` | VARCHAR(20) | 手机号 |
| `balance` | DECIMAL(18,2) | 汇总余额（业务上等于名下所有 account.balance 之和；由业务侧维护）|
| `status` | TINYINT | 状态（1=启用 0=禁用）默认 1 |
| `created_at` | DATETIME | 创建时间 默认 CURRENT_TIMESTAMP |

**种子（5 条）**：U000001 张三 / U000002 李四 / U000003 王五 / U000004 赵六 / U000005 钱七

#### `demo_account`——用户账户表

一个用户可以有多个账户（活期、定期、代发工资等）。

| 字段 | 类型 | 含义 |
|---|---|---|
| `id` | BIGINT PK AUTO | 主键 |
| `user_id` | BIGINT | 外联 `demo_user.id`（无 FK 约束，业务层约束）|
| `account_no` | VARCHAR(32) UNIQUE | 账户编号（如 A000001-01）|
| `balance` | DECIMAL(18,2) | 单账户余额 |
| `created_at` | DATETIME | 开户时间 |

**种子（6 条）**：张三 2 账户 · 李四/王五/赵六/钱七 各 1 账户

#### `demo_txn`——交易流水表

每一次资金变动一条流水。

| 字段 | 类型 | 含义 |
|---|---|---|
| `id` | BIGINT PK AUTO | 主键 |
| `user_id` | BIGINT | 外联 `demo_user.id` |
| `amount` | DECIMAL(18,2) | 交易金额（正数=收入 · 负数=支出）|
| `txn_time` | DATETIME | 交易时间 默认 CURRENT_TIMESTAMP |

**种子（8 条）**：张三 3 笔（净额 4900）· 李四 2 笔（20000）· 王五/赵六/钱七 各 1 笔

> **注**：v1.1 会加 `account_id / txn_type / peer_user_id / remark` 等字段（Faker 10 万条一起做，用于分片场景压测）。

### 2.2 电商体系（商品 · 订单 · 明细 · 地址）

#### `demo_product`——商品表

| 字段 | 类型 | 含义 |
|---|---|---|
| `id` | BIGINT PK AUTO | 主键 |
| `product_code` | VARCHAR(32) UNIQUE | 商品编码（如 P001）|
| `name` | VARCHAR(128) | 商品名 |
| `category_code` | VARCHAR(32) | 品类编码 · 逻辑外联 `demo_dict.dict_code` (dict_type='product_category') |
| `price` | DECIMAL(18,2) | 单价 |
| `stock` | INT | 库存 |
| `status` | TINYINT | 状态（1=上架 0=下架）|
| `created_at` | DATETIME | 上架时间 |

**种子（10 条）**：iPhone/MacBook/AirPods/Kindle（ELECTRONICS）· Java核心/设计模式/算法导论（BOOK）· 巧克力/牛肉干/茶叶（FOOD）

#### `demo_order`——订单表

| 字段 | 类型 | 含义 |
|---|---|---|
| `id` | BIGINT PK AUTO | 主键 |
| `order_no` | VARCHAR(32) UNIQUE | 订单号（如 O2026072800001）|
| `user_id` | BIGINT | 外联 `demo_user.id` |
| `total_amount` | DECIMAL(18,2) | 订单总额 |
| `status` | VARCHAR(16) | 状态 · 逻辑外联 `demo_dict.dict_code` (dict_type='order_status')：CREATED/PAID/SHIPPED/DONE/CANCELED |
| `created_at` | DATETIME | 下单时间 |

**种子（7 条）**：张三 2 单（PAID / SHIPPED）· 李四 2 单（DONE / CREATED）· 王五 1 单（PAID）· 赵六 1 单（DONE）· 钱七 1 单（CANCELED）

#### `demo_order_item`——订单明细

一个订单包含多个商品行。

| 字段 | 类型 | 含义 |
|---|---|---|
| `id` | BIGINT PK AUTO | 主键 |
| `order_id` | BIGINT | 外联 `demo_order.id` |
| `product_id` | BIGINT | 外联 `demo_product.id` |
| `qty` | INT | 数量 |
| `unit_price` | DECIMAL(18,2) | 下单时单价（快照）|
| `subtotal` | DECIMAL(18,2) | 小计 = qty × unit_price |

**种子（10 条）**：10 个明细行分布在 7 个订单里

#### `demo_address`——用户收货地址

| 字段 | 类型 | 含义 |
|---|---|---|
| `id` | BIGINT PK AUTO | 主键 |
| `user_id` | BIGINT | 外联 `demo_user.id` |
| `receiver` | VARCHAR(64) | 收件人 |
| `phone` | VARCHAR(20) | 收件人电话 |
| `province` / `city` / `district` | VARCHAR(32) | 省 / 市 / 区 |
| `detail` | VARCHAR(255) | 详细地址 |
| `is_default` | TINYINT | 是否默认（1=是 0=否）|

**种子（4 条）**：张三 2 个地址（默认深圳）· 李四 默认北京 · 王五 默认上海 · 赵六/钱七 暂无

### 2.3 通用体系（字典 · 配置 · 日志）

#### `demo_dict`——字典表

通用 KV 字典，支持"分类 → 编码 → 名称"三段结构。

| 字段 | 类型 | 含义 |
|---|---|---|
| `id` | BIGINT PK AUTO | 主键 |
| `dict_type` | VARCHAR(32) | 字典类型（product_category / order_status / gender / …）|
| `dict_code` | VARCHAR(32) | 字典编码 |
| `dict_name` | VARCHAR(64) | 字典名称（展示用）|
| `sort_order` | INT | 排序 |
| — | UNIQUE(dict_type, dict_code) | 类型 + 编码唯一 |

**种子（8 条）**：`product_category` 3 条（ELECTRONICS/BOOK/FOOD）· `order_status` 5 条（CREATED/PAID/SHIPPED/DONE/CANCELED）

> 这张表也是 **FN-12 字典映射**（v0.2.0 已交付）的天然测试床。

#### `demo_config`——配置表（大文本 JSON）

| 字段 | 类型 | 含义 |
|---|---|---|
| `id` | BIGINT PK AUTO | 主键 |
| `config_key` | VARCHAR(64) UNIQUE | 配置键 |
| `config_value` | TEXT | 配置值（推荐 JSON 字符串）|
| `updated_at` | DATETIME | 最后修改时间 |

**种子（3 条）**：`site.name` / `promotion.banner` / `feature.flags`（都是 JSON）

#### `demo_log`——操作日志表

| 字段 | 类型 | 含义 |
|---|---|---|
| `id` | BIGINT PK AUTO | 主键 |
| `user_id` | BIGINT | 外联 `demo_user.id` |
| `action` | VARCHAR(32) | 操作类型（LOGIN / CREATE_ORDER / PAY_ORDER / …）|
| `target` | VARCHAR(64) | 操作对象（如 order:O2026072800001）|
| `detail` | VARCHAR(500) | 详情文本 |
| `log_time` | DATETIME | 时间 |

**种子（15 条）**：登录 / 下单 / 支付 / 取消 / 登出 / 改地址等混合日志

### 2.4 关联关系图

```
                            ┌─────────────┐
                    ┌───────┤  demo_user  ├────────┐
                    │       └───┬──┬──┬──┘        │
                    │           │  │  │           │
              ┌─────▼─────┐     │  │  │   ┌───────▼───────┐
              │demo_account│     │  │  │   │  demo_address │
              └───────────┘     │  │  │   └───────────────┘
                                │  │  │
                     ┌──────────┘  │  └────────────────┐
                     │             │                   │
              ┌──────▼──────┐  ┌───▼────────┐   ┌──────▼──────┐
              │   demo_txn  │  │  demo_log  │   │  demo_order │
              └─────────────┘  └────────────┘   └──────┬──────┘
                                                       │ 1─N
                                              ┌────────▼─────────┐
                                              │ demo_order_item  │
                                              └────────┬─────────┘
                                                       │ N─1
                                              ┌────────▼─────────┐
                                              │   demo_product   │──┐
                                              └──────────────────┘  │ category_code
                                                                    │ (逻辑外联)
                       ┌──────────────┐                              │
                       │  demo_dict   │◄─────────────────────────────┘
                       │(独立字典表)  │◄───────── demo_order.status
                       └──────────────┘         (逻辑外联)

                       ┌──────────────┐
                       │ demo_config  │(独立 KV/JSON 配置表)
                       └──────────────┘
```

**关键关联汇总**：
- `demo_user.id` ← `demo_account / demo_txn / demo_address / demo_order / demo_log`（各表 `.user_id`）
- `demo_order.id` ← `demo_order_item.order_id`
- `demo_product.id` ← `demo_order_item.product_id`
- `demo_dict.dict_code` ← `demo_product.category_code`（dict_type='product_category'）· `demo_order.status`（dict_type='order_status'）—— 逻辑外联，需在 JOIN 时同时匹配 dict_type

---

## 三、连表 CRUD 测试场景（先在 MySQL 客户端跑通）

> 建议先在 DBeaver / Navicat 里**用 MySQL 直接跑一遍**，理解每条 SQL 的效果和期望结果。跑通后再在第四节把它们**配成 PG 接口**，就能对比理解 PG 平台生成的 SQL 是否等价。**种子数据由 pg-testkit init 自动灌好，不需要手工准备**。

### 场景 0 · 环境准备（选做）

若之前跑过写场景导致数据乱了，一键回到干净种子：

```bash
curl -X POST http://localhost:8081/testkit/demo-db/reset
```

或验证当前种子在位：

```sql
USE pg_demo;
SELECT
  (SELECT COUNT(*) FROM demo_user)       AS user_cnt,   -- 期望 5
  (SELECT COUNT(*) FROM demo_account)    AS account_cnt,-- 期望 6
  (SELECT COUNT(*) FROM demo_txn)        AS txn_cnt,    -- 期望 8
  (SELECT COUNT(*) FROM demo_product)    AS product_cnt,-- 期望 10
  (SELECT COUNT(*) FROM demo_order)      AS order_cnt,  -- 期望 7
  (SELECT COUNT(*) FROM demo_order_item) AS item_cnt,   -- 期望 10
  (SELECT COUNT(*) FROM demo_address)    AS addr_cnt,   -- 期望 4
  (SELECT COUNT(*) FROM demo_dict)       AS dict_cnt,   -- 期望 8
  (SELECT COUNT(*) FROM demo_config)     AS config_cnt, -- 期望 3
  (SELECT COUNT(*) FROM demo_log)        AS log_cnt;    -- 期望 15
```

### 场景 1 · 查询：INNER JOIN 两表

**需求**：列出所有"有账户的用户"，显示用户姓名 + 账户号 + 单账户余额。

```sql
SELECT
  u.user_no,
  u.name,
  a.account_no,
  a.balance AS account_balance
FROM demo_user u
INNER JOIN demo_account a ON u.id = a.user_id
ORDER BY u.user_no, a.account_no;
```

**期望结果**（6 行）：

| user_no | name | account_no | account_balance |
|---|---|---|---|
| U000001 | 张三 | A000001-01 | 6000.00 |
| U000001 | 张三 | A000001-02 | 4000.00 |
| U000002 | 李四 | A000002-01 | 20000.00 |
| U000003 | 王五 | A000003-01 | 5000.00 |
| U000004 | 赵六 | A000004-01 | 8000.00 |
| U000005 | 钱七 | A000005-01 | 3000.00 |

### 场景 2 · 查询：LEFT JOIN + 聚合（GROUP BY）

**需求**：列出**所有用户**（包括没交易记录的），显示交易笔数 + 交易净额。

```sql
SELECT
  u.user_no,
  u.name,
  COUNT(t.id)                 AS txn_count,
  IFNULL(SUM(t.amount), 0.00) AS net_amount
FROM demo_user u
LEFT JOIN demo_txn t ON u.id = t.user_id
GROUP BY u.id, u.user_no, u.name
ORDER BY u.user_no;
```

**期望**：

| user_no | name | txn_count | net_amount |
|---|---|---|---|
| U000001 | 张三 | 3 | 4900.00 |
| U000002 | 李四 | 2 | 20000.00 |
| U000003 | 王五 | 1 | 5000.00 |
| U000004 | 赵六 | 1 | 8000.00 |
| U000005 | 钱七 | 1 | 3000.00 |

### 场景 3 · 查询：三表 JOIN + 窗口函数

**需求**：列出每个用户名下每个账户的**账户余额**和**总交易净额**（同一用户名下所有账户的净额会相同，因为交易目前挂在用户上）。

```sql
SELECT
  u.user_no,
  u.name,
  a.account_no,
  a.balance                                             AS account_balance,
  IFNULL(SUM(t.amount) OVER (PARTITION BY u.id), 0.00)  AS user_total_txn
FROM demo_user u
INNER JOIN demo_account a ON u.id = a.user_id
LEFT  JOIN demo_txn     t ON u.id = t.user_id
ORDER BY u.user_no, a.account_no;
```

> ⚠️ 窗口函数 `SUM(...) OVER (...)` 需要 MySQL 8.0+。若是 5.7，改用子查询：
> ```sql
> LEFT JOIN (SELECT user_id, SUM(amount) AS s FROM demo_txn GROUP BY user_id) tx
>   ON u.id = tx.user_id
> ```

### 场景 4 · 查询：4 表 JOIN（订单全息 · 用户 + 状态字典 + 明细 + 商品）

**需求**：列出所有订单：订单号 / 买家 / 中文状态 / 总额 / 商品清单 / 明细行数。

```sql
SELECT
  o.order_no,
  u.name                                                       AS buyer,
  ds.dict_name                                                 AS status_cn,
  o.total_amount,
  GROUP_CONCAT(CONCAT(p.name, ' x', oi.qty) SEPARATOR ' | ')   AS items,
  COUNT(oi.id)                                                 AS item_count
FROM demo_order o
INNER JOIN demo_user u   ON o.user_id = u.id
LEFT  JOIN demo_dict ds  ON ds.dict_type = 'order_status' AND ds.dict_code = o.status
LEFT  JOIN demo_order_item oi ON o.id = oi.order_id
LEFT  JOIN demo_product p     ON oi.product_id = p.id
GROUP BY o.id, o.order_no, u.name, ds.dict_name, o.total_amount
ORDER BY o.order_no;
```

**期望**（7 行）：

| order_no | buyer | status_cn | total_amount | items | item_count |
|---|---|---|---|---|---|
| O2026072800001 | 张三 | 已支付 | 5999.00 | iPhone 15 x1 | 1 |
| O2026072800002 | 张三 | 已发货 | 196.00 | Java核心技术卷I x1 \| 设计模式 x1 | 2 |
| O2026072800003 | 李四 | 已完成 | 7999.00 | MacBook Air x1 | 1 |
| O2026072800004 | 李四 | 已创建 | 128.00 | Java核心技术卷I x1 | 1 |
| O2026072800005 | 王五 | 已支付 | 1999.00 | AirPods Pro x1 | 1 |
| O2026072800006 | 赵六 | 已完成 | 460.00 | 巧克力礼盒 x2 \| 设计模式 x1 \| 牛肉干 x2 | 3 |
| O2026072800007 | 钱七 | 已取消 | 128.00 | Java核心技术卷I x1 | 1 |

> ⚠️ `items` 列内商品顺序由 MySQL `GROUP_CONCAT` 内部实现决定，**不保证与上表一致**；若要稳定顺序，改成 `GROUP_CONCAT(... ORDER BY oi.id SEPARATOR ' | ')`。行数、item_count、total_amount 是稳定的。

### 场景 5 · 查询：品类销量排行（4 表 JOIN + 过滤 + 聚合）

**需求**：各**已成单**品类的销售额、销量、涉及订单数（排除取消和未支付）。

```sql
SELECT
  dc.dict_name                       AS category,
  COUNT(DISTINCT oi.order_id)        AS order_count,
  SUM(oi.qty)                        AS total_qty,
  SUM(oi.subtotal)                   AS total_sales
FROM demo_order_item oi
INNER JOIN demo_product p ON oi.product_id = p.id
INNER JOIN demo_dict    dc ON dc.dict_type = 'product_category' AND dc.dict_code = p.category_code
INNER JOIN demo_order   o  ON oi.order_id = o.id
WHERE o.status IN ('PAID','SHIPPED','DONE')
GROUP BY dc.dict_name
ORDER BY total_sales DESC;
```

**期望**：

| category | order_count | total_qty | total_sales |
|---|---|---|---|
| 电子产品 | 3 | 3 | 15997.00 |
| 食品 | 1 | 4 | 392.00 |
| 图书 | 2 | 3 | 264.00 |

### 场景 6 · 查询：用户 + 默认收货地址（LEFT JOIN 带条件）

**需求**：列出所有用户及其**默认**收货地址（没设默认地址的用户也要显示）。注意 JOIN 条件带 `is_default = 1`。

```sql
SELECT
  u.user_no,
  u.name,
  u.phone                                            AS user_phone,
  ad.receiver,
  ad.phone                                           AS ship_phone,
  CONCAT_WS(' ', ad.province, ad.city, ad.district, ad.detail) AS ship_addr
FROM demo_user u
LEFT JOIN demo_address ad ON u.id = ad.user_id AND ad.is_default = 1
ORDER BY u.user_no;
```

**期望**：

| user_no | name | user_phone | receiver | ship_phone | ship_addr |
|---|---|---|---|---|---|
| U000001 | 张三 | 13800138000 | 张三 | 13800138000 | 广东省 深圳市 南山区 科技园路 1 号 |
| U000002 | 李四 | 13900139000 | 李四 | 13900139000 | 北京市 北京市 朝阳区 望京 SOHO T2 |
| U000003 | 王五 | 13700137000 | 王五 | 13700137000 | 上海市 上海市 浦东新区 陆家嘴环路 |
| U000004 | 赵六 | 13600136000 | NULL | NULL | NULL |
| U000005 | 钱七 | 13500135000 | NULL | NULL | NULL |

### 场景 7 · 新增：多步事务（新用户 + 主账户 + 首笔存款）

**需求**：一次业务动作：**注册用户 U000099 → 给他开一个账户 → 记一笔首存 5000**。三步任意一步失败都要回滚。

```sql
START TRANSACTION;

-- 1. 建用户
INSERT INTO demo_user (user_no, name, gender, phone, balance)
VALUES ('U000099', '孙九', 1, '13400134000', 5000.00);
SET @new_uid = LAST_INSERT_ID();

-- 2. 开账户
INSERT INTO demo_account (user_id, account_no, balance)
VALUES (@new_uid, 'A000099-01', 5000.00);

-- 3. 首存流水
INSERT INTO demo_txn (user_id, amount)
VALUES (@new_uid, 5000.00);

COMMIT;

-- 验证
SELECT u.name, a.account_no, a.balance, t.amount
FROM demo_user u
JOIN demo_account a ON u.id = a.user_id
JOIN demo_txn     t ON u.id = t.user_id
WHERE u.user_no = 'U000099';
```

### 场景 8 · 修改：转账（张三 A000001-01 → 李四 A000002-01 转 500）

**需求**：改两个账户余额 + 改两个用户汇总余额 + 记两条交易流水，事务保证一致性。

```sql
START TRANSACTION;

SET @from_uid = (SELECT id FROM demo_user    WHERE user_no    = 'U000001');
SET @to_uid   = (SELECT id FROM demo_user    WHERE user_no    = 'U000002');
SET @from_aid = (SELECT id FROM demo_account WHERE account_no = 'A000001-01');
SET @to_aid   = (SELECT id FROM demo_account WHERE account_no = 'A000002-01');
SET @amount   = 500.00;

-- 校验：出账账户余额是否够
SELECT balance INTO @from_bal FROM demo_account WHERE id = @from_aid;
-- 若 @from_bal < @amount 应 ROLLBACK；这里假设充足

UPDATE demo_account SET balance = balance - @amount WHERE id = @from_aid;
UPDATE demo_account SET balance = balance + @amount WHERE id = @to_aid;
UPDATE demo_user    SET balance = balance - @amount WHERE id = @from_uid;
UPDATE demo_user    SET balance = balance + @amount WHERE id = @to_uid;

INSERT INTO demo_txn (user_id, amount) VALUES
  (@from_uid, -@amount),
  (@to_uid,    @amount);

COMMIT;

-- 验证两个用户当前状态
SELECT u.user_no, u.name, u.balance AS user_balance,
       a.account_no, a.balance AS account_balance
FROM demo_user u JOIN demo_account a ON u.id = a.user_id
WHERE u.user_no IN ('U000001', 'U000002');
```

**转账后期望**：张三 user_balance=9500 / A000001-01=5500 / A000001-02=4000（不变）· 李四 user_balance=20500 / A000002-01=20500

### 场景 9 · 删除：级联清理用户（先删依赖再删主）

**需求**：注销场景 7 建的 U000099 孙九，同时清理其名下所有账户和流水。

```sql
START TRANSACTION;

SET @uid = (SELECT id FROM demo_user WHERE user_no = 'U000099');

DELETE FROM demo_log     WHERE user_id = @uid;
DELETE FROM demo_address WHERE user_id = @uid;
DELETE FROM demo_txn     WHERE user_id = @uid;
DELETE FROM demo_account WHERE user_id = @uid;
DELETE FROM demo_user    WHERE id      = @uid;

COMMIT;

-- 验证：五处都应查不到
SELECT COUNT(*) FROM demo_user    WHERE user_no = 'U000099'; -- 期望 0
SELECT COUNT(*) FROM demo_account WHERE user_id = @uid;      -- 期望 0
SELECT COUNT(*) FROM demo_txn     WHERE user_id = @uid;      -- 期望 0
```

> **软删除做法**（生产更常用）：把 `UPDATE demo_user SET status=0 WHERE id=@uid` 替代 DELETE，账户/流水保留供审计。当前 `demo_user` / `demo_product` 都有 `status` 字段。

---

## 四、把上述场景配成 PG 接口（管理台 9 步向导）

> PG 平台的 QueryBuilder / InsertBuilder / UpdateBuilder / DeleteBuilder **已支持多表 JOIN**（M2-3 / M2-7 交付并覆盖测试）。以下以**场景 1 · 连表查询**为例，走一遍在管理台把它配成 REST 接口的完整流程。其他场景思路一致，仅换 op_type + SQL 结构。

### 4.1 前置：把 pg_demo 添加为业务数据源

1. 浏览器打开 http://localhost:5173 · 登录 `admin` / `Admin@123`
2. 侧边栏 → **接口开发 → 数据源管理 → 新建**
3. 填：
   - 名称：`pg_demo（DemoDb）`
   - 类型：`MySQL`
   - JDBC URL：`jdbc:mysql://localhost:3306/pg_demo?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8`
   - 用户名：`root` · 密码：`qwe12345`
4. 点"测试连接" → 通过 → 保存

PG 会自动 AES 加密密码写入 `powergateway_config.db_connection`。

### 4.2 走 9 步向导配"查用户+账户"接口（对应场景 1）

侧边栏 → **接口开发 → 接口列表 → 新建接口向导**，按步骤：

| Step | 名称 | 本例填法 |
|:---:|---|---|
| 1 | 基础信息 | 名称 `查询用户账户列表` · 路径 `/demo/user-accounts` · op_type `QUERY` · 数据源选 `pg_demo（DemoDb）` |
| 2 | 选表 | 主表 `demo_user`（别名 `u`）· 添加关联表 `demo_account`（别名 `a`）· JOIN 类型 `INNER` · ON 条件 `u.id = a.user_id` |
| 3 | 选字段 | 勾 `u.user_no` / `u.name` / `a.account_no` / `a.balance`（可给 balance 起显示别名 `account_balance`）|
| 4 | 查询条件 | 可选：留空 = 查全部；或加 `u.status = 1`（只查启用用户）|
| 5 | 排序分页 | 排序 `u.user_no ASC, a.account_no ASC`；是否分页看需求 |
| 6 | 字段加工 | 例如把 `u.gender` 数字映射成"男/女"（本例没勾 gender 可跳过）|
| 7 | 结果结构 | 平铺 / 树形（默认平铺）|
| 8 | 缓存 & 审计 | 缓存 TTL 300s（默认）· 审计默认开 |
| 9 | 预览 & 发布 | 点"预览" → 应看到与场景 1 期望一致的 6 行结果 → "保存" → "发布"（状态 `draft → published`）|

### 4.3 调接口

发布后 PG 会暴露统一执行入口 `POST /api/exec/{interfaceId}`（不是 4.2 里填的 `path`，`path` 是给外部网关反向映射用的）：

```bash
# 先看你的接口 id
curl -s -b "satoken=<你的登录 cookie>" \
  "http://localhost:8080/api/interface-config/list?keyword=用户账户列表"

# 假设 id = 15，调用
curl -s -X POST -b "satoken=<...>" \
  -H "Content-Type: application/json" \
  -d '{}' \
  http://localhost:8080/api/exec/15
```

或者在 Swagger UI 里（http://localhost:8080/swagger-ui.html）直接找 `/api/exec/{id}` 端点点 Try it out。

### 4.4 其他场景怎么配

| 场景 | op_type | 建议接口名 | 关键要点 |
|---|---|---|---|
| 2 · LEFT JOIN + 聚合 | QUERY | 用户交易汇总 | Step 2 用 `LEFT JOIN`；Step 3 用聚合列（`COUNT(t.id)` / `SUM(t.amount)`）+ GROUP BY `u.id` |
| 3 · 三表 JOIN + 窗口 | QUERY | 用户全息视图 | Step 2 加两条 JOIN；Step 3 显示三表混合字段 |
| 4 · 4 表订单全息 | QUERY | 订单看板 | Step 2 加 3 条 JOIN（含带条件 JOIN：`dict_type='order_status'`）；Step 3 用 GROUP_CONCAT 聚合明细 |
| 5 · 品类销量排行 | QUERY | 品类销售报表 | Step 2 加 3 条 JOIN；Step 4 加 `o.status IN (...)` 过滤；Step 3 聚合列 |
| 6 · 用户+默认地址 | QUERY | 用户收货簿 | Step 2 用带条件的 LEFT JOIN（`is_default = 1` 放 ON 而非 WHERE，否则退化成 INNER JOIN）|
| 7 · 新用户+账户+交易 | INSERT | 用户开户 + 首存 | 当前 InsertBuilder 单接口对应单表；**多表事务性写入拆 3 个接口 + 前端串调**，或走"编排接口"（v0.3.0 规划中，未交付）|
| 8 · 转账 | UPDATE | 账户转账 | 同上；转账要 4 步串调 + 前端事务补偿，PG 暂无跨接口事务；单接口能配的是"单表条件 update" |
| 9 · 级联删除 | DELETE | 用户注销 | 同上；分 5 个 DELETE 接口串调 |

> **当前平台边界（重要）**：`INSERT` / `UPDATE` / `DELETE` 接口每个只映射一张表。**跨表事务性写操作**（场景 7/8/9）在 v0.2.x 只能靠"前端 / 网关串调多个接口"完成，PG 侧无跨接口事务。**编排接口 / 事务组** 归属 v0.3.0+ 规划。查询侧（QUERY）已完整支持多表 JOIN，无此限制。

---

## 五、数据校验与重置

### 5.1 每个场景跑完后的数据校验

用 `POST /test/db/query`（pg-testkit 提供的 JDBC 直连接口）跑校验 SQL，或直接开 DBeaver：

```bash
# 用 pg-testkit 跑校验（避免开客户端）
curl -s -X POST http://localhost:8081/test/db/query \
  -H "Content-Type: application/json" \
  -d '{
    "datasource": "demo",
    "sql": "SELECT COUNT(*) AS c FROM demo_user"
  }'
```

> 需要 pg-testkit 的 `TestApiController` 支持 `datasource=demo`；若报错说不支持，先用 DBeaver 或 `mysql -u root -pqwe12345 pg_demo -e "SELECT..."` 兜底。

### 5.2 一键重置到干净状态

```bash
# TRUNCATE 全部 10 张 demo_* 表后重灌全部种子（5 用户 / 6 账户 / 8 交易 / 10 商品 / 7 订单 / 10 明细 / 4 地址 / 8 字典 / 3 配置 / 15 日志）
curl -X POST http://localhost:8081/testkit/demo-db/reset
```

想彻底删表重来（含重新建表 DDL）：

```bash
curl -X POST http://localhost:8081/testkit/demo-db/drop
curl -X POST http://localhost:8081/testkit/demo-db/init
```

### 5.3 查看审计日志（改动都有痕）

所有通过 PG 接口做的增删改都会异步写 `powergateway_config.sql_audit_log`：

```sql
SELECT id, interface_id, op_type, status, sql_text, cost_ms, op_time
FROM powergateway_config.sql_audit_log
ORDER BY id DESC LIMIT 20;
```

`before_snapshot` / `after_snapshot` 字段还保留了改动前后的行快照，便于回溯。

---

## 六、相关文档

- [连接配置速查](./连接配置速查.md) —— 全部数据库/服务连接信息 · pg_demo §三·B
- [v0.1.0-手工测试指南](./v0.1.0-手工测试指南.md) —— PG 全量手工回归脚本
- [pg-testkit README](../../pg-testkit/README.md) —— `/testkit/demo-db/*` 端点详解
- [架构说明](../02-设计/架构说明.md) —— QueryBuilder / InsertBuilder / 双层缓存 / 审计链路
- [变更记录 · CR-005](../03-开发/变更记录.md#cr-005) —— 本次 7 表补真 DDL 的变更详情
