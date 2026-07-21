package com.powergateway.testkit.demo;

import java.util.Map;

/**
 * TEST-1 · 样例业务库初始化器策略接口
 *
 * v1 只实现 MySQL；后续可扩展 Oracle / PostgreSQL / OceanBase 等。
 */
public interface DemoDbInitializer {

    /** 初始化：建库 + 建表 + 灌数据；幂等（已存在则跳过，或按 force 强制重灌） */
    void init(boolean force);

    /** 重置：TRUNCATE 所有 demo 表后重灌数据 */
    void reset();

    /** DROP：删除整个样例库（危险操作） */
    void drop();

    /** 各表行数统计 */
    Map<String, Long> stats();

    /** 描述：数据库类型 + 版本 */
    String describe();
}
