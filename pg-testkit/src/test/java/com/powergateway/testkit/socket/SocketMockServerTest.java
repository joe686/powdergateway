package com.powergateway.testkit.socket;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * SOCK-4 · SocketMockServer 静态工具单元测试(v0.3.0 · Task 10)。
 *
 * <p>@SpringBootTest 集成起停验证跑 pg-testkit 主启动类过重 · 这里只覆盖纯函数逻辑。</p>
 */
class SocketMockServerTest {

    @Test
    @DisplayName("extractFunctionId · 常规格式 · 提取正确")
    void extractFunctionId_常规() {
        String xml = "<?xml version=\"1.0\"?><Req><FunctionId>181345</FunctionId><Body>x</Body></Req>";
        assertEquals("181345", SocketMockServer.extractFunctionId(xml));
    }

    @Test
    @DisplayName("extractFunctionId · 值带空白 · 自动 trim")
    void extractFunctionId_trim() {
        String xml = "<Req><FunctionId>  180345  </FunctionId></Req>";
        assertEquals("180345", SocketMockServer.extractFunctionId(xml));
    }

    @Test
    @DisplayName("extractFunctionId · 不含 FunctionId · 返回 null")
    void extractFunctionId_不含_null() {
        assertNull(SocketMockServer.extractFunctionId("<Req><Other>x</Other></Req>"));
    }

    @Test
    @DisplayName("extractFunctionId · 多个只取第一个")
    void extractFunctionId_多个() {
        String xml = "<Req><FunctionId>1</FunctionId><Inner><FunctionId>2</FunctionId></Inner></Req>";
        assertEquals("1", SocketMockServer.extractFunctionId(xml));
    }
}
