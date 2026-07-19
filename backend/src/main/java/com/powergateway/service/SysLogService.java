package com.powergateway.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.powergateway.dao.SqlAuditLogMapper;
import com.powergateway.dao.SysLogHistoryMapper;
import com.powergateway.dao.SysLogMapper;
import com.powergateway.model.SqlAuditLog;
import com.powergateway.model.SysLog;
import com.powergateway.model.SysLogHistory;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 操作日志异步写入与查询服务（SYS-1）
 *
 * 实现机制：
 *   - LinkedBlockingQueue 作为缓冲区（容量 10000），防止日志写入阻塞业务主线程
 *   - 单个后台守护线程持续消费队列，逐条写入 sys_log 表
 *   - 队列满时直接丢弃（offer 非阻塞），保证业务不受影响
 *   - flushForTest() 仅供测试使用，同步消费队列剩余项目以便断言
 */
@Service
public class SysLogService {

    private static final int QUEUE_CAPACITY = 10000;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final LinkedBlockingQueue<SysLog> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);

    @Autowired private SysLogMapper sysLogMapper;
    @Autowired private SysLogHistoryMapper sysLogHistoryMapper;
    @Autowired private SqlAuditLogMapper auditLogMapper;

    @PostConstruct
    public void startConsumer() {
        Thread consumer = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    SysLog log = queue.take();
                    writeSafely(log);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "sys-log-consumer");
        consumer.setDaemon(true);
        consumer.start();
    }

    /**
     * 将操作日志投入队列（非阻塞）
     * 若队列已满则静默丢弃，日志不影响业务主链路
     */
    public void enqueue(SysLog log) {
        queue.offer(log);
    }

    /**
     * 仅供测试使用，严禁在生产代码中调用。
     * 同步消费队列剩余项目，使测试可以立即验证 DB 写入结果。
     * 生产环境的异步消费由守护线程自动处理。
     */
    public void flushForTest() {
        List<SysLog> pending = new ArrayList<>();
        queue.drainTo(pending);
        pending.forEach(this::writeSafely);
    }

    /**
     * 分页查询操作日志（支持 operator/module/level/时间范围 过滤）
     */
    public IPage<SysLog> list(String operator, String module, String level,
                               LocalDateTime startTime, LocalDateTime endTime,
                               int page, int size) {
        LambdaQueryWrapper<SysLog> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(operator))  w.like(SysLog::getOperator, operator);
        if (StringUtils.hasText(module))    w.eq(SysLog::getModule, module);
        if (StringUtils.hasText(level))     w.eq(SysLog::getLevel, level);
        if (startTime != null)              w.ge(SysLog::getOpTime, startTime);
        if (endTime != null)                w.le(SysLog::getOpTime, endTime);
        w.orderByDesc(SysLog::getOpTime);
        return sysLogMapper.selectPage(new Page<>(page, size), w);
    }

    /**
     * 分页查询归档日志（支持 operator/module/level/时间范围 过滤）
     */
    public IPage<SysLogHistory> listHistory(String operator, String module, String level,
                                             LocalDateTime startTime, LocalDateTime endTime,
                                             int page, int size) {
        LambdaQueryWrapper<SysLogHistory> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(operator))  w.like(SysLogHistory::getOperator, operator);
        if (StringUtils.hasText(module))    w.eq(SysLogHistory::getModule, module);
        if (StringUtils.hasText(level))     w.eq(SysLogHistory::getLevel, level);
        if (startTime != null)              w.ge(SysLogHistory::getOpTime, startTime);
        if (endTime != null)                w.le(SysLogHistory::getOpTime, endTime);
        w.orderByDesc(SysLogHistory::getOpTime);
        return sysLogHistoryMapper.selectPage(new Page<>(page, size), w);
    }

    /**
     * 分页查询 SQL 审计日志（支持 opType/result/时间范围 过滤）
     */
    public IPage<SqlAuditLog> listAuditLogs(String opType, String result,
                                              LocalDateTime startTime, LocalDateTime endTime,
                                              int page, int size) {
        LambdaQueryWrapper<SqlAuditLog> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(opType))  w.eq(SqlAuditLog::getOpType, opType);
        if (StringUtils.hasText(result))  w.eq(SqlAuditLog::getResult, result);
        if (startTime != null)            w.ge(SqlAuditLog::getOpTime, startTime);
        if (endTime != null)              w.le(SqlAuditLog::getOpTime, endTime);
        w.orderByDesc(SqlAuditLog::getOpTime);
        return auditLogMapper.selectPage(new Page<>(page, size), w);
    }

    /** FN-10 导出 SQL 审计日志列表 Excel（新增，不改动现有 exportExcel） */
    public byte[] exportAuditList(String opType, String result) {
        LambdaQueryWrapper<SqlAuditLog> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(opType)) w.eq(SqlAuditLog::getOpType, opType);
        if (StringUtils.hasText(result)) w.eq(SqlAuditLog::getResult, result);
        w.orderByDesc(SqlAuditLog::getOpTime);
        List<SqlAuditLog> rows = auditLogMapper.selectList(w);
        return com.powergateway.utils.ExcelExportUtil.export("SQL审计日志", java.util.Arrays.asList(
            new com.powergateway.utils.ExcelExportUtil.Column<>("接口ID",   SqlAuditLog::getInterfaceId),
            new com.powergateway.utils.ExcelExportUtil.Column<>("操作类型", SqlAuditLog::getOpType),
            new com.powergateway.utils.ExcelExportUtil.Column<>("操作人",   SqlAuditLog::getOperator),
            new com.powergateway.utils.ExcelExportUtil.Column<>("目标库",   SqlAuditLog::getTargetDb),
            new com.powergateway.utils.ExcelExportUtil.Column<>("目标表",   SqlAuditLog::getTargetTable),
            new com.powergateway.utils.ExcelExportUtil.Column<>("结果",     SqlAuditLog::getResult),
            new com.powergateway.utils.ExcelExportUtil.Column<>("时间",     r -> r.getOpTime() != null ? r.getOpTime().format(FMT) : "")
        ), rows);
    }

    /**
     * 导出操作日志为 Excel（支持同 list 的过滤条件）
     */
    public byte[] exportExcel(String operator, String module, String level,
                               LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<SysLog> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(operator))  w.like(SysLog::getOperator, operator);
        if (StringUtils.hasText(module))    w.eq(SysLog::getModule, module);
        if (StringUtils.hasText(level))     w.eq(SysLog::getLevel, level);
        if (startTime != null)              w.ge(SysLog::getOpTime, startTime);
        if (endTime != null)                w.le(SysLog::getOpTime, endTime);
        w.orderByDesc(SysLog::getOpTime);
        List<SysLog> logs = sysLogMapper.selectList(w);

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet sheet = wb.createSheet("操作日志");
            String[] headers = {"时间", "模块", "动作", "操作人", "IP", "级别", "耗时(ms)", "错误信息"};
            XSSFRow header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);

            for (int i = 0; i < logs.size(); i++) {
                SysLog log = logs.get(i);
                XSSFRow row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(log.getOpTime() != null ? log.getOpTime().format(FMT) : "");
                row.createCell(1).setCellValue(log.getModule() != null ? log.getModule() : "");
                row.createCell(2).setCellValue(log.getAction() != null ? log.getAction() : "");
                row.createCell(3).setCellValue(log.getOperator() != null ? log.getOperator() : "");
                row.createCell(4).setCellValue(log.getOpIp() != null ? log.getOpIp() : "");
                row.createCell(5).setCellValue(log.getLevel() != null ? log.getLevel() : "");
                row.createCell(6).setCellValue(log.getCostMs() != null ? log.getCostMs() : 0);
                row.createCell(7).setCellValue(log.getErrorMsg() != null ? log.getErrorMsg() : "");
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("导出 Excel 失败", e);
        }
    }

    private void writeSafely(SysLog log) {
        try {
            sysLogMapper.insert(log);
        } catch (Exception ignored) {
            // 日志写入失败静默处理，不影响业务
        }
    }
}
