package com.powergateway.service;

import com.powergateway.model.dto.*;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class HomeOverviewService {

    public HomeOverviewDTO getOverview(String dimension) {
        // 占位实现：后续 task 逐步填充各字段
        return new HomeOverviewDTO(
                new InterfaceStatsDTO(0L, 0L, 0L, 0L),
                new CallStatsDTO(0L, java.math.BigDecimal.ZERO, 0L, null),
                new CallTrendDTO(Collections.emptyList(), Collections.emptyList(), Collections.emptyList()),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList()
        );
    }
}
