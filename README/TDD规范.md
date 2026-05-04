# TDD 规范

## 强制工作流：Red → Green → Refactor

开发任何新功能或修复 Bug，**必须**严格按以下顺序执行，不得跳步：

1. **Red**：先写测试，运行确认失败（若测试直接通过，说明测试无效，需重写）
2. **Green**：写最小实现代码，使测试通过，不做多余实现
3. **Refactor**：测试全绿后再重构，重构后重新运行确认不退化

```bash
# 典型节奏（后端）
mvn test -Dtest=M17PortRouteTest          # 1. 先运行 → 确认 RED（FAILED）
# ... 写实现代码 ...
mvn test -Dtest=M17PortRouteTest          # 2. 再运行 → 确认 GREEN（PASSED）
mvn test                                  # 3. 全量回归，确认无退化
```

## 测试文件约定

- **命名**：`{单元编号}{功能}Test.java`，放在 `src/test/java/com/powergateway/` 对应包下
  - 工具类测试：`M11FormatConverterTest`、`M13FieldProcessorTest`
  - Service 测试：`M17PortRouteServiceTest`
  - Controller 测试（MockMvc）：`M17PortRouteControllerTest`
- **必须加**：`@ActiveProfiles("test")`，切换到 H2 内存库，禁止连接生产 MySQL/Redis
- **测试用例顺序**：正常路径 → 边界值 → 异常/错误场景

## 分层测试策略

| 层级 | 注解 | 说明 |
|------|------|------|
| 工具类（`utils/`） | 无 Spring 注解，纯 JUnit5 | `FormatConverter`、`FieldProcessor` 等无依赖的工具直接 `new` 实例测试 |
| Service 层 | `@SpringBootTest` + H2 | 验证业务逻辑、数据库读写；使用 `@Transactional` 自动回滚 |
| Controller 层 | `@WebMvcTest` + `MockMvc` | 验证请求映射、参数校验、响应结构；用 `@MockBean` 隔离 Service |

## 每个交付单元的测试验收门槛

每个单元提交前，测试必须覆盖其**验收标准**中列出的所有场景（见 `README/需求拆分与最小实现方案.md`）。核心逻辑（`utils/`、`service/`）覆盖率目标 ≥ 80%。
