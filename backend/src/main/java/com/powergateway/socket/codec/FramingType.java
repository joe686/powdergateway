package com.powergateway.socket.codec;

/**
 * TCP Socket 分帧策略枚举(v0.3.0 SOCK-1 · Q5=C 三种全支持)。
 *
 * <p>接口配置期必选一种 · 无默认值。</p>
 */
public enum FramingType {

    /** 以 XML 根标签自闭合(如 {@code </Transaction>})作为帧边界 */
    XML_BOUNDARY,

    /** 4 字节大端定长头 · 头值为 payload 字节数 */
    LENGTH_PREFIX_BE4,

    /** 8 字节大端定长头 · 头值为 payload 字节数(用户 2026-08-02 补充实证) */
    LENGTH_PREFIX_BE8;

    /**
     * 从字符串解析 FramingType(接口配置 JSON 里的 framing 字段)。
     *
     * @param value 字符串值(大小写敏感):xml_boundary / length_prefix_be4 / length_prefix_be8
     * @return 对应枚举
     * @throws IllegalArgumentException 值不合法
     */
    public static FramingType parse(String value) {
        if (value == null) {
            throw new IllegalArgumentException("framing 字段必填 · 无默认值");
        }
        switch (value.toLowerCase()) {
            case "xml_boundary":
                return XML_BOUNDARY;
            case "length_prefix_be4":
                return LENGTH_PREFIX_BE4;
            case "length_prefix_be8":
                return LENGTH_PREFIX_BE8;
            default:
                throw new IllegalArgumentException("framing 仅支持 xml_boundary / length_prefix_be4 / length_prefix_be8 · 收到:" + value);
        }
    }
}
