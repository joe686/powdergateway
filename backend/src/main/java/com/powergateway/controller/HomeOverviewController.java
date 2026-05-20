package com.powergateway.controller;

import com.powergateway.common.Result;
import com.powergateway.model.dto.HomeOverviewDTO;
import com.powergateway.service.HomeOverviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/home")
@Tag(name = "首页概览", description = "AUX-2 首页聚合数据接口")
public class HomeOverviewController {

    @Autowired private HomeOverviewService service;

    @GetMapping("/overview")
    @Operation(summary = "获取首页概览数据")
    public Result<HomeOverviewDTO> overview(
            @RequestParam(defaultValue = "today") String dimension) {
        return Result.success(service.getOverview(dimension));
    }
}
