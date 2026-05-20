package com.powergateway.service;

import com.powergateway.model.dto.CallStatsDTO;
import com.powergateway.model.dto.CallTrendDTO;
import com.powergateway.model.dto.HomeOverviewDTO;
import com.powergateway.model.dto.InterfaceStatsDTO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collections;

@Service
public class HomeOverviewService {

    public HomeOverviewDTO getOverview(String dimension) {
        // 占位实现：后续 task 逐步填充各字段
        return new HomeOverviewDTO(
                new InterfaceStatsDTO(0L, 0L, 0L, 0L),
                new CallStatsDTO(0L, BigDecimal.ZERO, 0L, null),
                new CallTrendDTO(Collections.emptyList(), Collections.emptyList(), Collections.emptyList()),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList()
        );
    }
}
