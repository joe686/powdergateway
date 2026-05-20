package com.powergateway.model.dto;

import com.powergateway.model.PerfAlert;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class HomeOverviewDTO {
    private InterfaceStatsDTO    interfaceStats;
    private CallStatsDTO         callStats;
    private CallTrendDTO         callTrend;
    private List<OpTypeCountDTO> opTypeDistribution;
    private List<SlowInterfaceDTO> topSlowInterfaces;
    private List<PerfAlert>      activeAlerts;
}
