package com.fiveok.express.order.scheduler;

import com.fiveok.express.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 超时订单结算调度器。
 * <p>
 * 每 5 分钟扫一次"配送中但已过 deadline 且还没标记超时"的订单，
 * 防止快递员故意拖延不点完成而让用户卡在等待状态。
 * </p>
 * <p>
 * 频率选择 5 分钟的理由：
 * <ul>
 *   <li>更频繁（每分钟）：DB 压力大，且超时认定误差也只有 1 分钟级，意义不大</li>
 *   <li>更稀疏（每小时）：用户感知到"超时但没免单"的窗口太长，体验差</li>
 *   <li>5 分钟：综合最佳，工业界常见取值</li>
 * </ul>
 * </p>
 * <p>
 * <b>多机部署注意</b>：当前是单机定时任务。如果以后部署多个实例，
 * 需要换成 ShedLock 或类似机制保证只有一个节点执行（或者直接接 XXL-Job）。
 * 但对当前模块化单体规模来说，单机调度足够。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutScheduler {

    private final OrderService orderService;

    /**
     * 每 5 分钟跑一次。fixedDelay 而不是 fixedRate：
     * 上一次跑得慢的话，宁可延后下一次，也不要并发触发。
     */
    @Scheduled(fixedDelay = 5 * 60 * 1000L, initialDelay = 30 * 1000L)
    public void scanAndSettle() {
        try {
            int n = orderService.settleExpiredOrders();
            if (n > 0) {
                log.info("[定时] 本轮结算超时订单 {} 单", n);
            }
        } catch (Exception e) {
            log.error("[定时] 超时订单扫描失败", e);
        }
    }
}
