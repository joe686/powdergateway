package com.powergateway.socket.codec;

import com.powergateway.exception.BusinessException;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * TCP Socket 编码白名单(v0.3.0 SOCK-1 · Q6=B 双编码全支持)。
 *
 * <p>接口配置期必选一种 · 无默认值。</p>
 */
public final class CharsetSupport {

    private static final Set<String> WHITELIST = new HashSet<>(Arrays.asList("UTF-8", "GBK"));

    private CharsetSupport() {
    }

    /**
     * 按名称获取 Charset · 校验白名单。
     *
     * @param name 编码名(大小写不敏感):UTF-8 / GBK
     * @return 对应 Charset
     * @throws BusinessException 编码不在白名单
     */
    public static Charset of(String name) {
        if (name == null) {
            throw new BusinessException("charset 字段必填 · 无默认值");
        }
        String upper = name.toUpperCase();
        if (!WHITELIST.contains(upper)) {
            throw new BusinessException("charset 仅支持 UTF-8 / GBK · 收到:" + name);
        }
        return Charset.forName(upper);
    }

    /**
     * 判断白名单包含该编码。
     *
     * @param name 编码名
     * @return true 表示合法
     */
    public static boolean isSupported(String name) {
        return name != null && WHITELIST.contains(name.toUpperCase());
    }
}
