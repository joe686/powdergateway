# FN-12 × M1-3 集成 · DictMappingProcessor 设计（v0.2.0 ②）

> **单元**：v0.2.0 ② · **CR 依据**：[CR-001](../../06-项目管理/待办与缺陷池.md#cr-001-字典转换配置字段字典映射)
> **前置**：v0.2.0 ①（[FN-12 后端 spec](./2026-07-26-FN-12-dict-mapping-backend-design.md)）已交付（CHG-028 · commits c6fc91e..4a47245）
> **设计日期**：2026-07-27 · **状态**：✅ 已 brainstorm · 待 writing-plans

---

## 一、范围与边界

### 1.1 做

1. `ProcessRuleType` 枚举加值 `DICT_MAP`
2. 新增 `DictMappingProcessor` 实现 `FieldProcessStrategy`（`@Component` 自动被 `FieldProcessor` 装配到 EnumMap · Strategy 模式复用现有 M1-3 骨架）
3. `FieldProcessController.ruleTypes()` 追加 1 条 `RuleTypeDesc` 让前端能看到 DICT_MAP + 参数说明
4. **单元 mock 测试** `M13DictMappingProcessorTest.java`：Mockito mock `DictMappingService.lookup`，4 场景
5. **集成 SpringBoot 测试** `FN12ProcessorIntegrationTest.java`：`@SpringBootTest` + H2，完整 process_rule JSON 走 processBatch → lookup → H2 全链路

### 1.2 不做（划出 v0.2.0 ②）

| 项 | 归属 |
|---|---|
| 前端 `FieldProcess.vue` / 向导集成 | v0.2.0 ③ |
| FN-09 × FN-12 Excel 联动 | v0.2.0 ④ |
| ConvertService 修改 | 无需（`processBatch` 抛 `BusinessException` 自动经 `GlobalExceptionHandler`）|

---

## 二、DictMappingProcessor 核心逻辑

**位置**：`backend/src/main/java/com/powergateway/utils/processor/DictMappingProcessor.java`

```java
@Component
public class DictMappingProcessor implements FieldProcessStrategy {

    @Autowired private DictMappingService dictMappingService;

    @Override public ProcessRuleType ruleType() { return ProcessRuleType.DICT_MAP; }

    @Override
    public String process(String value, Map<String, String> params) {
        // 空值透传：源字段本身为 null/空时不 lookup，让用户用 default 策略处理
        if (value == null || value.isEmpty()) return value;

        String system  = params.get("system");
        String dictKey = params.get("dictKey");
        String dirStr  = params.get("direction");

        if (system == null || dictKey == null || dirStr == null) {
            throw new BusinessException(400,
                "DICT_MAP 参数缺失：system/dictKey/direction 均必填");
        }
        int direction;
        try {
            direction = Integer.parseInt(dirStr);
        } catch (NumberFormatException e) {
            throw new BusinessException(400,
                "DICT_MAP direction 必须为整数 1 或 2，实际=" + dirStr);
        }

        DictMappingLookupResult r =
            dictMappingService.lookup(system, dictKey, direction, value);
        if (r == null) {
            throw new BusinessException(400, String.format(
                "字典 %s 值 %s 在系统 %s 未定义映射（direction=%d）",
                dictKey, value, system, direction));
        }
        return r.getTargetValue();
    }
}
```

**关键设计原则**：

| 决策 | 理由 |
|---|---|
| **空值透传**（不 lookup） | 源字段是 null 时"字典未映射"不是真实业务错误；让用户用 default 策略处理 |
| **参数级 Guard 早报错** | Service.lookup 已有 v0.2.0 ① final fix 的 Guard，Processor 再兜一层可提供更友好的错误消息 |
| **direction 参数是 String** | `ProcessRule.params` 结构是 `Map<String, String>`，只能存字符串，Processor 内 `Integer.parseInt` |
| **miss 抛 BusinessException(400)** | 严格模式，冒泡到 GlobalExceptionHandler，前端得 400 带具体错误消息 |

---

## 三、ProcessRuleType 枚举扩展

**修改**：`backend/src/main/java/com/powergateway/utils/processor/ProcessRuleType.java`

在枚举末尾追加：

```java
/**
 * 字典转换（FN-12 · v0.2.0 ②）
 * params: system (对端系统标识) · dictKey (字典标识) · direction (1=出向 2=入向)
 */
DICT_MAP
```

---

## 四、FieldProcessController.ruleTypes() 追加

**修改**：`backend/src/main/java/com/powergateway/controller/FieldProcessController.java` 的 `ruleTypes()` 方法 `list` 数组末尾追加：

```java
new RuleTypeDesc(ProcessRuleType.DICT_MAP, "字典转换",
    "system: 对端系统标识（如 CIF）; dictKey: 字典标识（如 GENDER）; direction: 1(出向) 或 2(入向)")
```

前端 `FieldProcess.vue` / `InterfaceWizard.vue` 步骤 6 消费此接口即可自动看到 DICT_MAP 选项（v0.2.0 ③ 落地时验证）。

---

## 五、测试策略

### 5.1 单元 mock 测试 · `M13DictMappingProcessorTest.java`

**位置**：`backend/src/test/java/com/powergateway/M13DictMappingProcessorTest.java`

**风格**：对齐 `M13FieldProcessorTest` 纯工具类风格 —— 直接 `new DictMappingProcessor()` + Mockito mock 注入 `DictMappingService`，无 Spring 上下文。

**4 用例**：
1. `mock_命中_返回target` — mock lookup 返 `new DictMappingLookupResult("1","男")` · process("M", params) 返 "1"
2. `mock_未命中_抛BusinessException400` — mock lookup 返 null · process("X", params) 抛异常含 "未定义映射"
3. `参数缺失_无system_抛400` — params 里没有 "system" · 抛异常含 "system/dictKey/direction 均必填"
4. `direction非整数_抛400` — params direction="abc" · 抛异常含 "direction 必须为整数"

### 5.2 集成 SpringBoot 测试 · `FN12ProcessorIntegrationTest.java`

**位置**：`backend/src/test/java/com/powergateway/FN12ProcessorIntegrationTest.java`

**风格**：`@SpringBootTest + @ActiveProfiles("test") + @Transactional`，走完整 Spring 装配链 · H2 真实 lookup · Redis fallback 到 DB。

**2 用例**：
1. `完整链路_processRule JSON_命中`：
   - 先 `dictMappingService.save(req)` 预置字典（CIF/GENDER/1/M→1）
   - 构造 `Map<String, List<ProcessRule>>` 含 DICT_MAP 规则
   - 调 `fieldProcessor.processBatch(fieldValues={"gender":"M"}, rules)` → 期望输出 `{"gender":"1"}`
2. `完整链路_processRule JSON_未命中_抛400`：
   - 无预置字典
   - 调 processBatch → 断言抛 `BusinessException` message 含 "字典 GENDER 值 M ... 未定义映射"

### 5.3 规模目标

- 新增 6 用例（4 mock + 2 integration）
- v0.2.0 ① 后基线 580 + 6 = 期望 586 全绿

---

## 六、异常传播与空值语义

| 情况 | Processor 行为 | 上游可见 |
|---|---|---|
| **命中** | 返 target_value | 转换成功，返 Result.success |
| **miss** | 抛 `BusinessException(400, "字典 ... 未定义映射")` | GlobalExceptionHandler → Result.fail(400) |
| **参数缺失** | 抛 `BusinessException(400, "DICT_MAP 参数缺失...")` | Result.fail(400) |
| **direction 非整数** | 抛 `BusinessException(400, "direction 必须为整数...")` | Result.fail(400) |
| **null/空源值** | 透传原值（不 lookup） | 转换继续，用户自行处理空值 |
| **Redis 挂了** | Service 层已降级到 DB fallback | 用户无感 |

---

## 七、依赖与影响面

| 项 | 影响 |
|---|---|
| **新增 Maven 依赖** | 无 |
| **修改既有代码** | `ProcessRuleType.java`（+1 枚举）· `FieldProcessController.java`（+1 RuleTypeDesc）· 无其他既有类被改 |
| **新增文件** | `DictMappingProcessor.java` · `M13DictMappingProcessorTest.java` · `FN12ProcessorIntegrationTest.java` |
| **既有测试影响** | 0 影响（新增枚举值不影响既有 5 个策略的 EnumMap 装配） |

---

## 八、Task 拆分（预告 · 详见 writing-plans 输出）

预估 4 Task · 0.5 人日：

| Task | 内容 | 用例累计 |
|:-:|---|:-:|
| 1 | ProcessRuleType.DICT_MAP 枚举 + DictMappingProcessor 类骨架 + 首个 mock 测试（命中路径） | 1 |
| 2 | 补齐 3 个 mock 测试（miss / 参数缺失 / direction 非整数） | 4 |
| 3 | 集成 SpringBoot 测试 2 用例 + FieldProcessController.ruleTypes 追加 | 6 |
| 4 | 全量回归 + CHG-029 归档 + 待办 v0.2.0 ② 勾掉 | 6 |

---

## 九、CHG-029 归档规划

完成后新增 `CHG-029` 到 [变更记录.md](../../03-开发/变更记录.md)：
- 类型：扩展既有单元（M1-3 FieldProcessor 加新策略）
- 关联：[CR-001](../../06-项目管理/待办与缺陷池.md#cr-001-字典转换配置字段字典映射)
- 影响单元：M1-3（扩展策略）· FN-12（复用 Service）
- 归入：v0.2.0 ②

---

## 十、相关文档

- [v0.2.0 ① FN-12 后端 spec](./2026-07-26-FN-12-dict-mapping-backend-design.md)
- [CR-001](../../06-项目管理/待办与缺陷池.md#cr-001-字典转换配置字段字典映射)
- [路线图 § v0.2.0](../../06-项目管理/路线图.md)
- 实施 TDD 分解将保存至 `docs/03-开发/任务计划/2026-07-27-FN-12-processor.md`（writing-plans 阶段产出）
