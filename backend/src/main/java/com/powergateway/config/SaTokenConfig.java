package com.powergateway.config;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Sa-Token 拦截器配置
 * 拦截所有 /api/**，排除登录/登出和健康检查接口
 * 未登录时直接写 JSON 响应（401），避免异常传播到 MockMvc 时被包成 NestedServletException
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    private static final String UNAUTH_BODY =
            "{\"code\":401,\"message\":\"未登录或登录已过期，请重新登录\",\"data\":null}";

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> StpUtil.checkLogin()) {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                                     Object handler) throws Exception {
                try {
                    return super.preHandle(request, response, handler);
                } catch (NotLoginException e) {
                    response.setStatus(HttpServletResponse.SC_OK);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write(UNAUTH_BODY);
                    return false;
                }
            }
        })
        .addPathPatterns("/api/**")
        .excludePathPatterns("/api/auth/login", "/api/auth/logout", "/api/health");
    }
}
