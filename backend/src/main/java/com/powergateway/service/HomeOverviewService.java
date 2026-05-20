package com.powergateway.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.powergateway.dao.InterfaceConfigMapper;
import com.powergateway.model.InterfaceConfig;
import com.powergateway.model.dto.CallStatsDTO;
import com.powergateway.model.dto.CallTrendDTO;
import com.powergateway.model.dto.HomeOverviewDTO;
import com.powergateway.model.dto.InterfaceStatsDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collections;

@Service
public class HomeOverviewService {

    @Autowired private InterfaceConfigMapper interfaceConfigMapper;

    public HomeOverviewDTO getOverview(String dimension) {
        InterfaceStatsDTO interfaceStats = computeInterfaceStats();

        return new HomeOverviewDTO(
                interfaceStats,
                new CallStatsDTO(0L, BigDecimal.ZERO, 0L, null),
                new CallTrendDTO(Collections.emptyList(), Collections.emptyList(), Collections.emptyList()),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList()
        );
    }

    private InterfaceStatsDTO computeInterfaceStats() {
        long draft     = interfaceConfigMapper.selectCount(
                new LambdaQueryWrapper<InterfaceConfig>().eq(InterfaceConfig::getStatus, "draft"));
        long published = interfaceConfigMapper.selectCount(
                new LambdaQueryWrapper<InterfaceConfig>().eq(InterfaceConfig::getStatus, "published"));
        long disabled  = interfaceConfigMapper.selectCount(
                new LambdaQueryWrapper<InterfaceConfig>().eq(InterfaceConfig::getStatus, "disabled"));
        return new InterfaceStatsDTO(draft + published + disabled, draft, published, disabled);
    }
}
