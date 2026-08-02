package com.powergateway.testkit.socket;

/**
 * TCP Socket Mock 规则(v0.3.0 SOCK-4 · pg-testkit)。
 *
 * <p>YAML 映射:socket-mock.rules[]。请求 XML 里提取 {@code <FunctionId>} 与本规则匹配 · 返 responseFile 内容。</p>
 */
public class SocketMockRule {

    /** 匹配的 FunctionId(与请求 XML 里 <FunctionId>xxx</FunctionId> 相等触发) */
    private String functionId;

    /** 应答报文文件路径(classpath 相对路径 · 如 mocks/181345-response.xml) */
    private String responseFile;

    public String getFunctionId() { return functionId; }
    public void setFunctionId(String functionId) { this.functionId = functionId; }
    public String getResponseFile() { return responseFile; }
    public void setResponseFile(String responseFile) { this.responseFile = responseFile; }
}
