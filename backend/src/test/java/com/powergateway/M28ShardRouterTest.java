package com.powergateway;

import com.powergateway.exception.BusinessException;
import com.powergateway.model.dto.ShardRuleJson;
import com.powergateway.model.dto.ShardRouteResult;
import com.powergateway.utils.ShardRouter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.*;

@ActiveProfiles("test")
@DisplayName("M2-8 ShardRouter 路由算法单元测试")
class M28ShardRouterTest {

    // ─── 辅助构建方法 ─────────────────────────────────────────────────────────────

    /** 构建取模路由规则：divisor=16，两段：0-7→db1，8-15→db2 */
    private ShardRuleJson buildModuloRule(int indexPadding) {
        ShardRuleJson rule = new ShardRuleJson();
        rule.setRoutingField("userId");

        ShardRuleJson.AlgorithmConfig algo = new ShardRuleJson.AlgorithmConfig();
        algo.setType("MODULO");
        algo.setDivisor(16);
        rule.setAlgorithm(algo);

        ShardRuleJson.DbSegment seg1 = new ShardRuleJson.DbSegment();
        seg1.setDbConnectionId(1L);
        seg1.setTablePrefix("orders_");
        seg1.setIndexStart(0);
        seg1.setIndexEnd(7);
        seg1.setIndexPadding(indexPadding);

        ShardRuleJson.DbSegment seg2 = new ShardRuleJson.DbSegment();
        seg2.setDbConnectionId(2L);
        seg2.setTablePrefix("orders_");
        seg2.setIndexStart(8);
        seg2.setIndexEnd(15);
        seg2.setIndexPadding(indexPadding);

        rule.setDbSegments(Arrays.asList(seg1, seg2));
        return rule;
    }

    /** 构建范围路由规则：1-999→db1/trade_001，1000-1999→db2/trade_002 */
    private ShardRuleJson buildRangeRule() {
        ShardRuleJson rule = new ShardRuleJson();
        rule.setRoutingField("tradeId");

        ShardRuleJson.AlgorithmConfig algo = new ShardRuleJson.AlgorithmConfig();
        algo.setType("RANGE");
        rule.setAlgorithm(algo);

        ShardRuleJson.ShardItem s1 = new ShardRuleJson.ShardItem();
        s1.setRangeStart(1L); s1.setRangeEnd(999L);
        s1.setDbConnectionId(1L); s1.setTableName("trade_001");

        ShardRuleJson.ShardItem s2 = new ShardRuleJson.ShardItem();
        s2.setRangeStart(1000L); s2.setRangeEnd(1999L);
        s2.setDbConnectionId(2L); s2.setTableName("trade_002");

        rule.setShards(Arrays.asList(s1, s2));
        return rule;
    }

    // ─── 取模路由 ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("取模路由_低位索引_路由到第一库")
    void modulo_lowIndex_routesToDb1() {
        ShardRouteResult r = ShardRouter.route(buildModuloRule(0), "3");
        assertThat(r.getDbConnectionId()).isEqualTo(1L);
        assertThat(r.getTableName()).isEqualTo("orders_3");
    }

    @Test
    @DisplayName("取模路由_边界值indexStart=0")
    void modulo_boundary_zero() {
        ShardRouteResult r = ShardRouter.route(buildModuloRule(0), "0");
        assertThat(r.getDbConnectionId()).isEqualTo(1L);
        assertThat(r.getTableName()).isEqualTo("orders_0");
    }

    @Test
    @DisplayName("取模路由_边界值indexEnd=7")
    void modulo_boundary_seven() {
        ShardRouteResult r = ShardRouter.route(buildModuloRule(0), "7");
        assertThat(r.getDbConnectionId()).isEqualTo(1L);
        assertThat(r.getTableName()).isEqualTo("orders_7");
    }

    @Test
    @DisplayName("取模路由_高位索引_路由到第二库")
    void modulo_highIndex_routesToDb2() {
        ShardRouteResult r = ShardRouter.route(buildModuloRule(0), "8");
        assertThat(r.getDbConnectionId()).isEqualTo(2L);
        assertThat(r.getTableName()).isEqualTo("orders_8");
    }

    @Test
    @DisplayName("取模路由_大数字取模后命中低位")
    void modulo_largeNumber_moduloToLow() {
        // 32 % 16 = 0 → orders_0
        ShardRouteResult r = ShardRouter.route(buildModuloRule(0), "32");
        assertThat(r.getDbConnectionId()).isEqualTo(1L);
        assertThat(r.getTableName()).isEqualTo("orders_0");
    }

    @Test
    @DisplayName("取模路由_indexPadding=2_表名补零")
    void modulo_padding2_tableNamePadded() {
        ShardRouteResult r = ShardRouter.route(buildModuloRule(2), "3");
        assertThat(r.getTableName()).isEqualTo("orders_03");
    }

    @Test
    @DisplayName("取模路由_空分段列表_抛BusinessException")
    void modulo_emptySegments_throws() {
        ShardRuleJson rule = buildModuloRule(0);
        rule.setDbSegments(Collections.emptyList());
        assertThatThrownBy(() -> ShardRouter.route(rule, "3"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未配置分段");
    }

    // ─── 范围路由 ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("范围路由_命中第一段")
    void range_firstShard() {
        ShardRouteResult r = ShardRouter.route(buildRangeRule(), "500");
        assertThat(r.getDbConnectionId()).isEqualTo(1L);
        assertThat(r.getTableName()).isEqualTo("trade_001");
    }

    @Test
    @DisplayName("范围路由_边界值rangeStart")
    void range_boundary_start() {
        assertThat(ShardRouter.route(buildRangeRule(), "1").getTableName()).isEqualTo("trade_001");
    }

    @Test
    @DisplayName("范围路由_边界值rangeEnd")
    void range_boundary_end() {
        assertThat(ShardRouter.route(buildRangeRule(), "999").getTableName()).isEqualTo("trade_001");
    }

    @Test
    @DisplayName("范围路由_命中第二段")
    void range_secondShard() {
        ShardRouteResult r = ShardRouter.route(buildRangeRule(), "1000");
        assertThat(r.getDbConnectionId()).isEqualTo(2L);
        assertThat(r.getTableName()).isEqualTo("trade_002");
    }

    @Test
    @DisplayName("范围路由_超出所有范围_抛BusinessException")
    void range_outOfRange_throws() {
        assertThatThrownBy(() -> ShardRouter.route(buildRangeRule(), "9999"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无匹配分片范围");
    }

    // ─── pad 工具方法 ──────────────────────────────────────────────────────────────

    @Test @DisplayName("pad_不补零") void pad_zero() {
        assertThat(ShardRouter.pad(3, 0)).isEqualTo("3");
    }

    @Test @DisplayName("pad_补2位") void pad_two() {
        assertThat(ShardRouter.pad(3, 2)).isEqualTo("03");
        assertThat(ShardRouter.pad(15, 2)).isEqualTo("15");
    }
}
