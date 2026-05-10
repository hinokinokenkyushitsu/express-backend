package com.fiveok.express.order.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fiveok.express.order.dto.CreateOrderRequest;
import com.fiveok.express.order.dto.OrderView;
import com.fiveok.express.order.entity.Order;

/**
 * 订单服务接口
 * <p>
 * 注意：本项目按【模块化单体】组织，order 模块自己负责整个订单生命周期：
 * 创建、查询、抢单、状态流转、取消，以及超时的认定与结算。
 * 创建订单接口由同学C 编写（在 createOrder 中体现），其余由同学D 实现。
 * </p>
 */
public interface OrderService extends IService<Order> {

    /** 创建订单（同学 C 负责） */
    Order createOrder(Long userId, CreateOrderRequest request);

    /** 我的订单（分页，带用户信息） */
    IPage<OrderView> getMyOrders(Long userId, int pageNum, int pageSize);

    /** 抢单大厅（分页，带用户信息） */
    IPage<OrderView> getHallOrders(int pageNum, int pageSize);

    /** 快递员我的任务（分页，按 courier_id 查询） */
    IPage<OrderView> getCourierTasks(Long courierId, int pageNum, int pageSize);

    /**
     * 抢单（双重锁防并发）。
     *
     * @param promisedMinutes 快递员承诺的送达分钟数；
     *                        紧急单 ≤ 30，普通单 ≤ 120，否则抛业务异常。
     *                        被封禁的快递员不能抢单。
     */
    void grabOrder(Long orderId, Long courierId, Integer promisedMinutes);

    /**
     * 更新状态（CAS + 流转规则校验）。
     * <p>
     * 当快递员把订单标记为已完成时，如果当前已超过 deadline，
     * 会一并触发"超时结算"：订单 amount 清零、累加快递员 overdue_count，
     * 必要时封禁。
     * </p>
     */
    void updateStatus(Long orderId, Integer status, Long operatorId, String role);

    /** 取消订单 */
    void cancelOrder(Long orderId, Long userId);

    /**
     * 扫描并结算所有"配送中且已过 deadline 但还没标记超时"的订单。
     * <p>
     * 由 OrderTimeoutScheduler 每 5 分钟调用一次。
     * 这是为了防止快递员故意拖着不点完成，让用户被卡在"等待"中。
     * </p>
     *
     * @return 本次新结算的超时订单数
     */
    int settleExpiredOrders();
}
