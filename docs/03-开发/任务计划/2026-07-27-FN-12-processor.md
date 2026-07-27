# FN-12 × M1-3 集成 · DictMappingProcessor 实施计划（v0.2.0 ②）

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` to implement this plan task-by-task. Steps use checkbox syntax.

**Goal:** 新增 `DictMappingProcessor` 实现 `FieldProcessStrategy`，让 M1-3 报文转换 + M2 可视化接口的字段加工步骤能配置"字典转换"规则，调 `DictMappingService.lookup` 完成源值→目标值映射。

**Architecture:** 复用 M1-3 现有 Strategy 模式（`@Component` 自动扫描到 `EnumMap`），不改核心 `FieldProcessor` 引擎。`ProcessRuleType` 加枚举值 `DICT_MAP`，`FieldProcessController.ruleTypes()` 加 1 条描述让前端看到，`DictMappingProcessor` 40 行内实现三层 Guard（空值透传 → 参数校验 → miss 抛 400）。

**Tech Stack:** Spring Boot 2.7.18 · Strategy 模式 · Mockito · JUnit 5 · H2(test)

**Spec 源**：[`docs/02-设计/详细设计/2026-07-27-FN-12-processor-design.md`](../../02-设计/详细设计/2026-07-27-FN-12-processor-design.md)

## Global Constraints

- JDK 1.8+ · Spring Boot 2.7.18
- 对话与代码注释：中文
- 测试类 `@ActiveProfiles("test")`（集成测试）· 单元测试可无 Spring
- 一 task 一 commit
- 端口固定 8080 不变
- **禁止新增 Maven 依赖**（Mockito 已在 spring-boot-starter-test 传递依赖里）
- **不改核心** `FieldProcessor.java`：完全靠 Strategy 模式 `@Component` 扩展
- 复用 v0.2.0 ① 已交付的 `DictMappingService.lookup` 契约（signature 稳定，含 v0.2.0 ① final fix 的 4 处 Guard）
- 空值透传：`value == null || value.isEmpty()` 时不 lookup，直接返回原值
- miss 抛 `BusinessException(400, "字典 ... 未定义映射")` — 冒泡到 GlobalExceptionHandler

---

## 文件结构预览

**Create（3 个新文件）**：

| 文件 | 职责 |
|---|---|
| `backend/src/main/java/com/powergateway/utils/processor/DictMappingProcessor.java` | Strategy 实现 · 40 行内 · 三层 Guard |
| `backend/src/test/java/com/powergateway/M13DictMappingProcessorTest.java` | 单元 mock 测试 · 4 用例 · 无 Spring |
| `backend/src/test/java/com/powergateway/FN12ProcessorIntegrationTest.java` | 集成测试 · 2 用例 · @SpringBootTest + H2 |

**Modify（2 个既有文件）**：
- `backend/src/main/java/com/powergateway/utils/processor/ProcessRuleType.java`（末尾追加枚举值 `DICT_MAP`）
- `backend/src/main/java/com/powergateway/controller/FieldProcessController.java`（`ruleTypes()` 方法追加 1 条 `RuleTypeDesc`）

---

## Task 1: 枚举扩展 + Processor 骨架 + 首个 mock 命中测试

**Files:**
- Modify: `backend/src/main/java/com/powergateway/utils/processor/ProcessRuleType.java`（末尾追加 `DICT_MAP`）
- Create: `backend/src/main/java/com/powergateway/utils/processor/DictMappingProcessor.java`
- Create: `backend/src/test/java/com/powergateway/M13DictMappingProcessorTest.java`

**Interfaces:**
- Consumes: `FieldProcessStrategy` 接口 (M1-3)、`ProcessRuleType` 枚举、`DictMappingService.lookup(String,String,Integer,String) → DictMappingLookupResult` (v0.2.0 ①)、`BusinessException(int,String)`
- Produces:
  - `ProcessRuleType.DICT_MAP` 新值
  - `DictMappingProcessor implements FieldProcessStrategy`：`process(value, params)` 返 String · `ruleType()` 返 `DICT_MAP`

- [ ] **Step 1.1: 追加枚举值**

修改 `ProcessRuleType.java`，在最后一个枚举值后加：

```java
    /**
     * 字典转换（FN-12 · v0.2.0 ②）
     * params: system (对端系统标识) · dictKey (字典标识) · direction (1=出向 2=入向)
     */
    DICT_MAP
```

- [ ] **Step 1.2: 写首个 mock 测试（Red）**

`backend/src/test/java/com/powergateway/M13DictMappingProcessorTest.java`：

```java
package com.powergateway;

import com.powergateway.model.dto.DictMappingLookupResult;
import com.powergateway.service.DictMappingService;
import com.powergateway.utils.processor.DictMappingProcessor;
import com.powergateway.utils.processor.ProcessRuleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** M1-3 DictMappingProcessor 单元测试（Mockito mock DictMappingService） */
class M13DictMappingProcessorTest {

    private DictMappingService mockService;
    private DictMappingProcessor processor;

    @BeforeEach
    void setUp() {
        mockService = Mockito.mock(DictMappingService.class);
        processor = new DictMappingProcessor();
        // 手工注入 mock（@Autowired 字段用反射设置）
        try {
            java.lang.reflect.Field f = DictMappingProcessor.class.getDeclaredField("dictMappingService");
            f.setAccessible(true);
            f.set(processor, mockService);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void mock_命中_返回target() {
        Mockito.when(mockService.lookup("CIF", "GENDER", 1, "M"))
               .thenReturn(new DictMappingLookupResult("1", "男"));

        Map<String, String> params = new HashMap<>();
        params.put("system", "CIF");
        params.put("dictKey", "GENDER");
        params.put("direction", "1");

        String result = processor.process("M", params);
        assertThat(result).isEqualTo("1");
        assertThat(processor.ruleType()).isEqualTo(ProcessRuleType.DICT_MAP);
    }
}
```

- [ ] **Step 1.3: 运行测试确认失败**

```bash
cd backend && mvn test -Dtest=M13DictMappingProcessorTest -q
```

预期：**编译失败**（`DictMappingProcessor` 类不存在）

- [ ] **Step 1.4: 创建 Processor 骨架实现**

`backend/src/main/java/com/powergateway/utils/processor/DictMappingProcessor.java`：

```java
package com.powergateway.utils.processor;

import com.powergateway.exception.BusinessException;
import com.powergateway.model.dto.DictMappingLookupResult;
import com.powergateway.service.DictMappingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 字典映射策略（FN-12 · v0.2.0 ②）
 * 集成 M1-3 字段加工引擎，让转换/接口字段可以配置字典转换规则。
 *
 * 参数：
 *   - system    对端系统标识（如 CIF）
 *   - dictKey   字典标识（如 GENDER）
 *   - direction 方向 "1"=出向 "2"=入向（字符串，因 ProcessRule.params 是 Map<String,String>）
 *
 * 语义：
 *   - value 空值（null 或 ""）→ 透传，不 lookup
 *   - 参数缺失 / direction 非法 / miss → 抛 BusinessException(400)
 */
@Component
public class DictMappingProcessor implements FieldProcessStrategy {

    @Autowired
    private DictMappingService dictMappingService;

    @Override
    public ProcessRuleType ruleType() {
        return ProcessRuleType.DICT_MAP;
    }

    @Override
    public String process(String value, Map<String, String> params) {
        // 空值透传（用户可用 default 策略处理源空值场景）
        if (value == null || value.isEmpty()) {
            return value;
        }

        String system  = params == null ? null : params.get("system");
        String dictKey = params == null ? null : params.get("dictKey");
        String dirStr  = params == null ? null : params.get("direction");

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

- [ ] **Step 1.5: 运行测试确认通过**

```bash
cd backend && mvn test -Dtest=M13DictMappingProcessorTest -q
```

预期：`Tests run: 1, Failures: 0`

- [ ] **Step 1.6: Commit**

```bash
git add backend/src/main/java/com/powergateway/utils/processor/ProcessRuleType.java \
        backend/src/main/java/com/powergateway/utils/processor/DictMappingProcessor.java \
        backend/src/test/java/com/powergateway/M13DictMappingProcessorTest.java
git commit -m "feat(FN-12): ProcessRuleType.DICT_MAP + DictMappingProcessor 骨架 + 命中测试 (Task 1)"
```

---

## Task 2: 补齐 3 个 mock 测试（miss · 参数缺失 · direction 非整数）

**Files:**
- Modify: `backend/src/test/java/com/powergateway/M13DictMappingProcessorTest.java`（追加 3 个 @Test）

**Interfaces:** 无新代码，纯测试补充

- [ ] **Step 2.1: 追加 3 个测试**

在 `M13DictMappingProcessorTest.java` 追加：

```java
    @Test
    void mock_未命中_抛BusinessException400() {
        Mockito.when(mockService.lookup(Mockito.anyString(), Mockito.anyString(),
                                        Mockito.anyInt(), Mockito.anyString()))
               .thenReturn(null);

        Map<String, String> params = new HashMap<>();
        params.put("system", "CIF");
        params.put("dictKey", "GENDER");
        params.put("direction", "1");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> processor.process("X", params))
            .isInstanceOf(com.powergateway.exception.BusinessException.class)
            .hasMessageContaining("未定义映射");
    }

    @Test
    void 参数缺失_无system_抛400() {
        Map<String, String> params = new HashMap<>();
        params.put("dictKey", "GENDER");
        params.put("direction", "1");
        // 缺 system

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> processor.process("M", params))
            .isInstanceOf(com.powergateway.exception.BusinessException.class)
            .hasMessageContaining("system/dictKey/direction 均必填");
    }

    @Test
    void direction非整数_抛400() {
        Map<String, String> params = new HashMap<>();
        params.put("system", "CIF");
        params.put("dictKey", "GENDER");
        params.put("direction", "abc");   // 非整数

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> processor.process("M", params))
            .isInstanceOf(com.powergateway.exception.BusinessException.class)
            .hasMessageContaining("direction 必须为整数");
    }
```

- [ ] **Step 2.2: 运行测试确认通过**

```bash
cd backend && mvn test -Dtest=M13DictMappingProcessorTest -q
```

预期：`Tests run: 4, Failures: 0`

- [ ] **Step 2.3: Commit**

```bash
git add backend/src/test/java/com/powergateway/M13DictMappingProcessorTest.java
git commit -m "test(FN-12): DictMappingProcessor 补齐 3 个边界 mock 用例 (Task 2)"
```

---

## Task 3: 集成 SpringBoot 测试 · 2 用例 + FieldProcessController.ruleTypes 追加

**Files:**
- Create: `backend/src/test/java/com/powergateway/FN12ProcessorIntegrationTest.java`
- Modify: `backend/src/main/java/com/powergateway/controller/FieldProcessController.java`（`ruleTypes()` 追加 1 条）

**Interfaces:**
- Consumes: `FieldProcessor.processBatch(Map<String,String>, Map<String,List<ProcessRule>>) → Map<String,String>` · `DictMappingService.save(DictMappingSaveRequest) → List<Long>`
- Produces: 无

- [ ] **Step 3.1: 写集成测试**

`backend/src/test/java/com/powergateway/FN12ProcessorIntegrationTest.java`：

```java
package com.powergateway;

import com.powergateway.exception.BusinessException;
import com.powergateway.model.dto.DictMappingSaveRequest;
import com.powergateway.service.DictMappingService;
import com.powergateway.utils.FieldProcessor;
import com.powergateway.utils.processor.ProcessRule;
import com.powergateway.utils.processor.ProcessRuleType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FN-12 × M1-3 集成测试（v0.2.0 ②）
 * 走完整 Spring 装配：FieldProcessor → DictMappingProcessor → DictMappingService.lookup → H2
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FN12ProcessorIntegrationTest {

    @Autowired private FieldProcessor fieldProcessor;
    @Autowired private DictMappingService dictMappingService;

    @Test
    void 完整链路_processRule_命中() {
        // 1. 预置字典 CIF/GENDER/1: M→1, F→0
        DictMappingSaveRequest req = new DictMappingSaveRequest();
        req.setSystemCode("CIF"); req.setDictKey("GENDER"); req.setDirection(1);
        req.setSourceValue("M");  req.setTargetValue("1"); req.setBidirectional(false);
        dictMappingService.save(req);

        // 2. 构造 process_rule
        ProcessRule rule = new ProcessRule();
        rule.setType(ProcessRuleType.DICT_MAP);
        Map<String, String> params = new HashMap<>();
        params.put("system", "CIF");
        params.put("dictKey", "GENDER");
        params.put("direction", "1");
        rule.setParams(params);

        Map<String, List<ProcessRule>> allRules = new HashMap<>();
        allRules.put("gender", Collections.singletonList(rule));

        // 3. 输入字段
        Map<String, String> fieldValues = new HashMap<>();
        fieldValues.put("gender", "M");

        // 4. 执行 processBatch
        Map<String, String> result = fieldProcessor.processBatch(fieldValues, allRules);

        assertThat(result.get("gender")).isEqualTo("1");
    }

    @Test
    void 完整链路_processRule_未命中_抛400() {
        // 不预置字典
        ProcessRule rule = new ProcessRule();
        rule.setType(ProcessRuleType.DICT_MAP);
        Map<String, String> params = new HashMap<>();
        params.put("system", "CIF");
        params.put("dictKey", "GENDER");
        params.put("direction", "1");
        rule.setParams(params);

        Map<String, List<ProcessRule>> allRules = new HashMap<>();
        allRules.put("gender", Collections.singletonList(rule));

        Map<String, String> fieldValues = new HashMap<>();
        fieldValues.put("gender", "M");

        assertThatThrownBy(() -> fieldProcessor.processBatch(fieldValues, allRules))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("未定义映射");
    }
}
```

- [ ] **Step 3.2: 追加 RuleTypeDesc 到 Controller**

修改 `FieldProcessController.java` `ruleTypes()` 方法内的 `List<RuleTypeDesc> list = Arrays.asList(...)`，在最后一条 `TYPE_CAST` 后加逗号 + 新条目：

```java
new RuleTypeDesc(ProcessRuleType.DICT_MAP, "字典转换",
    "system: 对端系统标识（如 CIF）; dictKey: 字典标识（如 GENDER）; direction: 1(出向) 或 2(入向)")
```

- [ ] **Step 3.3: 运行测试确认通过**

```bash
cd backend && mvn test -Dtest=FN12ProcessorIntegrationTest -q
```

预期：`Tests run: 2, Failures: 0`

- [ ] **Step 3.4: 全量回归**

```bash
cd backend && mvn test -q
```

预期：**586 全绿**（v0.2.0 ① 后基线 580 + 6 新）

- [ ] **Step 3.5: Commit**

```bash
git add backend/src/test/java/com/powergateway/FN12ProcessorIntegrationTest.java \
        backend/src/main/java/com/powergateway/controller/FieldProcessController.java
git commit -m "feat(FN-12): FN12ProcessorIntegrationTest + FieldProcessController 加 DICT_MAP 描述 (Task 3)"
```

---

## Task 4: 归档 CHG-029 + 待办勾掉 + push

**Files:**
- Modify: `docs/03-开发/变更记录.md`（追加 CHG-029）
- Modify: `docs/06-项目管理/待办与缺陷池.md`（勾掉 v0.2.0 ②）

- [ ] **Step 4.1: 追加 CHG-029**

在 `docs/03-开发/变更记录.md` 末尾追加：

```markdown
## CHG-029 · 2026-07-27 · FN-12 × M1-3 集成 DictMappingProcessor（v0.2.0 ②）

**类型**：扩展既有单元（M1-3 加新策略 + FN-12 复用）
**关联反馈**：[FB-039](../06-项目管理/反馈簿.md)
**CR**：[CR-001](../06-项目管理/待办与缺陷池.md#cr-001-字典转换配置字段字典映射)

**交付内容**：
- `ProcessRuleType` 加枚举 `DICT_MAP`
- 新增 `DictMappingProcessor implements FieldProcessStrategy`（`@Component` 自动装配）· 三层 Guard（空值透传 / 参数校验 / miss 400）
- `FieldProcessController.ruleTypes()` 追加 1 条 `RuleTypeDesc` 让前端能看到
- 新增 6 用例：4 mock 单元 + 2 SpringBoot 集成 = 从 580 → 586

**依赖**：无新增（复用 v0.2.0 ① DictMappingService.lookup 契约）
**关联设计**：[docs/02-设计/详细设计/2026-07-27-FN-12-processor-design.md](../02-设计/详细设计/2026-07-27-FN-12-processor-design.md)
**关联任务**：[docs/03-开发/任务计划/2026-07-27-FN-12-processor.md](./任务计划/2026-07-27-FN-12-processor.md)
```

- [ ] **Step 4.2: 勾掉待办 v0.2.0 ②**

修改 `docs/06-项目管理/待办与缺陷池.md`，把 `[ ] ② FN-12 × M1-3 集成` 改为：

```markdown
- [x] **② FN-12 × M1-3 集成** ✅ 2026-07-27 → CHG-029 · commits <Task1~3 SHA>
```

- [ ] **Step 4.3: Commit + Push**

```bash
git add docs/03-开发/变更记录.md docs/06-项目管理/待办与缺陷池.md
git commit -m "docs(项目管理): FN-12 × M1-3 集成交付归档 · CHG-029 · v0.2.0 ② 勾掉"
git push origin master
```

---

## 全体自检

**1. Spec 覆盖**：Spec § 1-9 全部对应到 Task 1-4。无 gap。

**2. Placeholder 扫描**：无 TBD/TODO。每 code step 有完整代码。

**3. 类型一致**：
- `DictMappingProcessor.process(String, Map<String,String>)` 全文一致
- `ruleType() → ProcessRuleType.DICT_MAP` 一致
- `DictMappingService.lookup(String, String, Integer, String) → DictMappingLookupResult` 与 v0.2.0 ① 一致
- `BusinessException(int, String)` 一致

---

## 相关文档

- [spec](../../02-设计/详细设计/2026-07-27-FN-12-processor-design.md)
- [v0.2.0 ① spec](../../02-设计/详细设计/2026-07-26-FN-12-dict-mapping-backend-design.md) · [plan](./2026-07-26-FN-12-backend.md)
- [CR-001](../../06-项目管理/待办与缺陷池.md#cr-001-字典转换配置字段字典映射)
- [路线图 § v0.2.0](../../06-项目管理/路线图.md)
