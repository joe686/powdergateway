package com.powergateway.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.powergateway.dao.InterfaceConfigMapper;
import com.powergateway.model.InterfaceConfig;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomiser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 动态将已发布接口注册到 Swagger 文档（M2-7）。
 * SpringDoc 每次请求 /v3/api-docs 时都会调用 customise()，自动反映最新发布状态。
 */
@Component
public class OpenApiDynamicCustomizer implements OpenApiCustomiser {

    @Autowired
    private InterfaceConfigMapper interfaceConfigMapper;

    @Override
    public void customise(OpenAPI openApi) {
        LambdaQueryWrapper<InterfaceConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InterfaceConfig::getStatus, "published");
        List<InterfaceConfig> published = interfaceConfigMapper.selectList(wrapper);

        for (InterfaceConfig config : published) {
            String path = "/api/exec/" + config.getId();
            if (openApi.getPaths() != null && openApi.getPaths().containsKey(path)) {
                continue; // 已由 ExecController 自动注册，跳过
            }

            Schema<?> paramsSchema = new Schema<>().type("object");
            Schema<?> bodySchema = new Schema<>().type("object")
                    .addProperties("params", paramsSchema);
            if ("SELECT".equals(config.getType())) {
                bodySchema.addProperties("page", new Schema<>().type("integer").example(1));
                bodySchema.addProperties("pageSize", new Schema<>().type("integer").example(20));
            }

            Operation op = new Operation()
                    .summary("[" + config.getType() + "] " + config.getName())
                    .description("自动发布的接口，类型：" + config.getType())
                    .addTagsItem("接口执行")
                    .requestBody(new RequestBody()
                            .content(new Content().addMediaType("application/json",
                                    new MediaType().schema(bodySchema))))
                    .responses(new ApiResponses()
                            .addApiResponse("200", new ApiResponse().description("执行成功")));

            PathItem pathItem = new PathItem().post(op);
            openApi.path(path, pathItem);
        }
    }
}
