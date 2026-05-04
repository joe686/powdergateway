package com.powergateway.service;

import com.powergateway.dao.SqlAuditLogMapper;
import com.powergateway.model.SqlAuditLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * SQL 审计日志异步写入服务（M2-9）
 *
 * 实现机制：
 *   - LinkedBlockingQueue 作为缓冲区（容量 10000），防止审计写入阻塞业务主线程
 *   - 单个后台守护线程持续消费队列，逐条写入审计库
 *   - 队列满时直接丢弃（offer 非阻塞），保证业务不受影响
 */
@Service
public class AuditLogService {

    private static final int QUEUE_CAPACITY = 10000;

    private final LinkedBlockingQueue<SqlAuditLog> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);

    @Autowired
    private SqlAuditLogMapper auditLogMapper;

    @PostConstruct
    public void startConsumer() {
        Thread consumer = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    SqlAuditLog log = queue.take(); // 阻塞直到有数据
                    writeToAuditDb(log);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "audit-log-consumer");
        consumer.setDaemon(true); // 守护线程，JVM 退出时不阻塞
        consumer.start();
    }

    /**
     * 将审计记录投入队列（非阻塞）
     * 若队列已满则静默丢弃，审计不影响业务主链路
     */
    public void enqueue(SqlAuditLog log) {
        queue.offer(log);
    }

    private void writeToAuditDb(SqlAuditLog log) {
        try {
            auditLogMapper.insert(log);
        } catch (Exception e) {
            // 审计写入失败静默处理，不影响业务
        }
    }
}
