package com.powergateway;

import com.powergateway.service.codec.PathExpression;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FN-11 Task 1 · PathExpression 工具 TDD 测试
 *
 * 覆盖 items[*].a 路径的读/写/分词/拼接，及边界条件。
 * 工具类无 Spring 上下文，快、独立。
 */
@ActiveProfiles("test")
class FN11PathExpressionTest {

    // ============ tokenize ============

    @Test
    void tokenize_普通嵌套路径() {
        assertThat(PathExpression.tokenize("a.b.c"))
                .containsExactly("a", "b", "c");
    }

    @Test
    void tokenize_含数组标记() {
        assertThat(PathExpression.tokenize("a.b[*].c"))
                .containsExactly("a", "b", "[*]", "c");
    }

    @Test
    void tokenize_叶子是数组标记() {
        assertThat(PathExpression.tokenize("items[*]"))
                .containsExactly("items", "[*]");
    }

    @Test
    void tokenize_单段() {
        assertThat(PathExpression.tokenize("foo"))
                .containsExactly("foo");
    }

    @Test
    void tokenize_空路径_返回空列表() {
        assertThat(PathExpression.tokenize("")).isEmpty();
    }

    @Test
    void tokenize_null路径_返回空列表() {
        assertThat(PathExpression.tokenize(null)).isEmpty();
    }

    // ============ join ============

    @Test
    void join_普通() {
        assertThat(PathExpression.join(Arrays.asList("a", "b", "c")))
                .isEqualTo("a.b.c");
    }

    @Test
    void join_含数组标记() {
        assertThat(PathExpression.join(Arrays.asList("a", "b", "[*]", "c")))
                .isEqualTo("a.b[*].c");
    }

    @Test
    void join_数组标记在叶子() {
        assertThat(PathExpression.join(Arrays.asList("items", "[*]")))
                .isEqualTo("items[*]");
    }

    @Test
    void join_空列表_返回空字符串() {
        assertThat(PathExpression.join(new ArrayList<>())).isEqualTo("");
    }

    @Test
    void join_null_返回空字符串() {
        assertThat(PathExpression.join(null)).isEqualTo("");
    }

    @Test
    void tokenize_join_往返一致() {
        String[] paths = {"a", "a.b", "a.b.c", "a.b[*].c", "items[*]", "root[*].nested[*].leaf"};
        for (String p : paths) {
            assertThat(PathExpression.join(PathExpression.tokenize(p)))
                    .as("回读路径应保持一致: %s", p)
                    .isEqualTo(p);
        }
    }

    // ============ read ============

    @Test
    void read_单层Map() {
        Map<String, Object> root = new HashMap<>();
        root.put("name", "alice");
        assertThat(PathExpression.read(root, "name")).isEqualTo("alice");
    }

    @Test
    void read_嵌套Map() {
        Map<String, Object> inner = new HashMap<>();
        inner.put("FunctionId", "170350");
        Map<String, Object> root = new HashMap<>();
        root.put("head", inner);
        assertThat(PathExpression.read(root, "head.FunctionId")).isEqualTo("170350");
    }

    @Test
    void read_数组_拿到List() {
        // {"a":{"b":[{"c":1},{"c":2}]}}
        Map<String, Object> item1 = new HashMap<>();
        item1.put("c", 1);
        Map<String, Object> item2 = new HashMap<>();
        item2.put("c", 2);
        Map<String, Object> b = new HashMap<>();
        b.put("b", Arrays.asList(item1, item2));
        Map<String, Object> root = new HashMap<>();
        root.put("a", b);

        Object result = PathExpression.read(root, "a.b[*].c");
        assertThat(result).isInstanceOf(List.class);
        assertThat((List<?>) result).containsExactly(1, 2);
    }

    @Test
    void read_数组作为叶子_返回List本身() {
        Map<String, Object> root = new HashMap<>();
        root.put("items", Arrays.asList("x", "y", "z"));
        Object result = PathExpression.read(root, "items[*]");
        assertThat(result).isInstanceOf(List.class);
        assertThat((List<?>) result).containsExactly("x", "y", "z");
    }

    @Test
    void read_路径不存在_返回null() {
        Map<String, Object> root = new HashMap<>();
        root.put("a", "hello");
        assertThat(PathExpression.read(root, "a.b.c")).isNull();
    }

    @Test
    void read_null根_返回null() {
        assertThat(PathExpression.read(null, "a.b")).isNull();
    }

    @Test
    void read_空路径_返回根本身() {
        Map<String, Object> root = new HashMap<>();
        root.put("x", 1);
        assertThat(PathExpression.read(root, "")).isSameAs(root);
    }

    // ============ write ============

    @Test
    void write_单层() {
        Map<String, Object> root = new LinkedHashMap<>();
        PathExpression.write(root, "name", "bob");
        assertThat(root).containsEntry("name", "bob");
    }

    @Test
    void write_嵌套自动创建中间Map() {
        Map<String, Object> root = new LinkedHashMap<>();
        PathExpression.write(root, "head.FunctionId", "170350");
        Object head = root.get("head");
        assertThat(head).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) head).get("FunctionId")).isEqualTo("170350");
    }

    @Test
    void write_数组_按索引展开写入元素() {
        // 目标：写入 "a.b[*].c" = [1, 2] → {"a":{"b":[{"c":1},{"c":2}]}}
        Map<String, Object> root = new LinkedHashMap<>();
        PathExpression.write(root, "a.b[*].c", Arrays.asList(1, 2));

        Object a = root.get("a");
        assertThat(a).isInstanceOf(Map.class);
        Object b = ((Map<?, ?>) a).get("b");
        assertThat(b).isInstanceOf(List.class);

        List<?> list = (List<?>) b;
        assertThat(list).hasSize(2);
        assertThat(list.get(0)).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) list.get(0)).get("c")).isEqualTo(1);
        assertThat(((Map<?, ?>) list.get(1)).get("c")).isEqualTo(2);
    }

    @Test
    void write_数组作为叶子_整段赋值() {
        Map<String, Object> root = new LinkedHashMap<>();
        PathExpression.write(root, "items[*]", Arrays.asList("x", "y"));
        Object items = root.get("items");
        assertThat(items).isInstanceOf(List.class);
        assertThat((List<?>) items).containsExactly("x", "y");
    }

    @Test
    void write_数组值不是List_抛异常() {
        Map<String, Object> root = new LinkedHashMap<>();
        assertThatThrownBy(() -> PathExpression.write(root, "a.b[*].c", "not-a-list"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("[*]");
    }

    @Test
    void write_读回_往返一致() {
        Map<String, Object> root = new LinkedHashMap<>();
        PathExpression.write(root, "a.b[*].c", Arrays.asList(10, 20, 30));
        Object read = PathExpression.read(root, "a.b[*].c");
        assertThat((List<?>) read).containsExactly(10, 20, 30);
    }

    @Test
    void write_null根_抛异常() {
        assertThatThrownBy(() -> PathExpression.write(null, "a", 1))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void write_空路径_抛异常() {
        Map<String, Object> root = new LinkedHashMap<>();
        assertThatThrownBy(() -> PathExpression.write(root, "", 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
