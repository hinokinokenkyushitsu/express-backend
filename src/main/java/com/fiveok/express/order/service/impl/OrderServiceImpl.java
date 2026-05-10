package com.fiveok.express.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fiveok.express.base.exception.BusinessException;
import com.fiveok.express.base.utils.I18nUtil;
import com.fiveok.express.base.utils.RedisLockUtil;
import com.fiveok.express.order.dto.CreateOrderRequest;
import com.fiveok.express.order.dto.OrderView;
import com.fiveok.express.order.entity.Order;
import com.fiveok.express.order.enums.OrderStatus;
import com.fiveok.express.order.enums.OrderUrgency;
import com.fiveok.express.order.mapper.OrderMapper;
import com.fiveok.express.order.mapper.OverdueScanRow;
import com.fiveok.express.order.service.OrderService;
import com.fiveok.express.user.api.UserFacade;     // ← 仅依赖 user 模块的 api 包
import com.fiveok.express.user.api.UserView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 订单服务实现
 * <p>
 * 通过 {@link UserFacade} 跨模块调用 user 模块——只依赖接口，不知道实现。
 * 假如某天 user 模块改成 RPC、改成另一个数据库，order 模块代码完全不用动。
 * 这就是模块化单体的核心收益：内部清晰，未来可拆。
 * </p>
 *
 * <h3>超时机制总览</h3>
 * <ol>
 *   <li>用户下单选 urgency（紧急/普通）</li>
 *   <li>快递员抢单时承诺 promisedMinutes，受 urgency 上限约束（紧急≤30, 普通≤120）</li>
 *   <li>抢单成功 → grab_time = NOW(), deadline = NOW() + promisedMinutes</li>
 *   <li>两条结算路径：
 *     <ul>
 *       <li>主动：快递员点"已完成"时若已过 deadline，则标 overdue=1 + amount=0 + 累加</li>
 *       <li>兜底：定时任务每 5 分钟扫一次，对"配送中但超时"的强制结算（防拖延）</li>
 *     </ul>
 *   </li>
 *   <li>累加超时数达到 5 → 用户模块自动封禁该快递员 7 天</li>
 *   <li>用 CAS overdue=0→1 保证一个订单只触发一次结算</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements OrderService {

    private final RedisLockUtil redisLockUtil;
    private final UserFacade userFacade;   // ← 跨模块依赖：只通过 Facade 访问 user 模块
    private final I18nUtil i18n;           // ← 用于状态名翻译

    /** 状态流转白名单（防止跳跃和回退） */
    private static final Map<Integer, Set<Integer>> ALLOWED_TRANSITIONS = Map.of(
            OrderStatus.PENDING.getCode(),     Set.of(OrderStatus.IN_PROGRESS.getCode(),
                                                     OrderStatus.CANCELLED.getCode()),
            OrderStatus.IN_PROGRESS.getCode(), Set.of(OrderStatus.COMPLETED.getCode())
    );

    /** 超时阈值：累计 5 次超时触发封禁 */
    private static final int OVERDUE_BAN_THRESHOLD = 5;
    /** 封禁时长：7 天 */
    private static final int BAN_DURATION_DAYS = 7;
    /** 定时任务单次扫描上限，避免积压时把内存撑爆 */
    private static final int OVERDUE_SCAN_BATCH = 200;

    @Override
    public Order createOrder(Long userId, CreateOrderRequest request) {
        Order order = new Order();
        order.setUserId(userId);
        order.setStationAddress(request.getStationAddress());
        order.setTargetAddress(request.getTargetAddress());
        order.setPickupCode(request.getPickupCode());
        order.setAmount(request.getAmount());
        order.setStatus(OrderStatus.PENDING.getCode());
        // urgency 默认为 0（普通）；DTO 已有 @Min/@Max 校验
        order.setUrgency(request.getUrgency() == null ? 0 : request.getUrgency());
        order.setOverdue(0);
        save(order);
        log.info("订单创建成功: orderId={}, userId={}, urgency={}",
                order.getId(), userId, order.getUrgency());
        return order;
    }

    @Override
    public IPage<OrderView> getMyOrders(Long userId, int pageNum, int pageSize) {
        IPage<Order> orderPage = page(
                new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getUserId, userId)
                        .orderByDesc(Order::getCreateTime)
        );
        return enrichWithUser(orderPage);
    }

    @Override
    public IPage<OrderView> getHallOrders(int pageNum, int pageSize) {
        IPage<Order> orderPage = page(
                new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getStatus, OrderStatus.PENDING.getCode())
                        // 紧急单优先展示，再按时间
                        .orderByDesc(Order::getUrgency)
                        .orderByAsc(Order::getCreateTime)
        );
        return enrichWithUser(orderPage);
    }

    @Override
    public IPage<OrderView> getCourierTasks(Long courierId, int pageNum, int pageSize) {
        IPage<Order> orderPage = page(
                new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getCourierId, courierId)
                        .orderByDesc(Order::getUpdateTime)
                        .orderByDesc(Order::getCreateTime)
        );
        return enrichWithUser(orderPage);
    }

    /**
     * 把订单分页结果转换为带用户信息的 OrderView 分页结果。
     * <p>关键：批量拉取用户信息（避免 N+1 查询），通过 UserFacade 跨模块。</p>
     */
    private IPage<OrderView> enrichWithUser(IPage<Order> orderPage) {
        List<Order> orders = orderPage.getRecords();
        if (orders.isEmpty()) {
            return orderPage.convert(o -> {
                OrderView v = new OrderView();
                v.setOrder(o);
                return v;
            });
        }

        // 1. 收集所有需要查询的 userId（去重，包含发单人 + 快递员）
        List<Long> userIds = orders.stream()
                .flatMap(o -> Stream.of(o.getUserId(), o.getCourierId()))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        // 2. 批量调用 UserFacade（一次 SQL 全部拉回）
        Map<Long, UserView> userMap = userFacade.findByIds(userIds);

        // 3. 拼装 OrderView
        return orderPage.convert(o -> {
            OrderView v = new OrderView();
            v.setOrder(o);
            v.setUser(userMap.get(o.getUserId()));
            if (o.getCourierId() != null) {
                v.setCourier(userMap.get(o.getCourierId()));
            }
            return v;
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void grabOrder(Long orderId, Long courierId, Integer promisedMinutes) {
        // 1) 封禁检查（最便宜的快速失败）
        if (userFacade.isCourierBanned(courierId)) {
            throw BusinessException.forbidden("courier.banned");
        }

        // 2) 取订单读 urgency，校验承诺时间是否在合法范围内
        //    这里读一次即可——抢单 SQL 自带 status=0 的并发保护，重复读不影响正确性。
        Order order = getById(orderId);
        if (order == null) {
            throw new BusinessException(404, "order.not.found");
        }
        OrderUrgency urgency = OrderUrgency.of(order.getUrgency() == null ? 0 : order.getUrgency());
        int max = urgency.getMaxPromisedMinutes();
        if (promisedMinutes == null || promisedMinutes < 1 || promisedMinutes > max) {
            String urgencyName = i18n.t("order.urgency." + urgency.getCode());
            throw new BusinessException(400, "order.promised.minutes.invalid",
                    urgencyName, max);
        }

        // 3) Redis 锁防"同一个快递员快速点两下"，下面 SQL 才是真正的并发安全屏障
        boolean locked = redisLockUtil.tryLock(orderId);
        if (!locked) {
            throw BusinessException.conflict("order.grab.too.fast");
        }
        try {
            int rows = baseMapper.grabOrder(orderId, courierId, promisedMinutes);
            if (rows == 0) {
                throw BusinessException.conflict("order.grab.taken");
            }
            log.info("抢单成功: orderId={}, courierId={}, promisedMinutes={}",
                    orderId, courierId, promisedMinutes);
        } finally {
            redisLockUtil.unlock(orderId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long orderId, Integer newStatus, Long operatorId, String role) {
        Order order = getById(orderId);
        if (order == null) {
            throw new BusinessException(404, "order.not.found");
        }

        // 权限校验：接单的快递员或管理员
        boolean isCourier = operatorId.equals(order.getCourierId());
        boolean isAdmin = "ADMIN".equals(role);
        if (!isCourier && !isAdmin) {
            throw BusinessException.forbidden("order.no.permission");
        }

        // 状态流转校验
        Integer oldStatus = order.getStatus();
        Set<Integer> allowed = ALLOWED_TRANSITIONS.getOrDefault(oldStatus, Set.of());
        if (!allowed.contains(newStatus)) {
            // 通过 I18nUtil 拼接两段已翻译的状态名作为参数
            String oldName = i18n.t("order.status." + oldStatus);
            String newName = i18n.t("order.status." + newStatus);
            throw new BusinessException(400, "order.status.illegal.transition",
                    oldName, newName);
        }

        // CAS 原子更新
        int rows = baseMapper.updateStatusCAS(orderId, oldStatus, newStatus);
        if (rows == 0) {
            throw BusinessException.conflict("order.status.changed");
        }
        log.info("订单状态更新: orderId={}, {} -> {}", orderId, oldStatus, newStatus);

        // 完成订单时：检查是否超时，是则触发结算（CAS 0→1 保证只算一次）
        if (Objects.equals(newStatus, OrderStatus.COMPLETED.getCode())) {
            tryMarkOverdue(order);
        }
    }

    /**
     * 完成或扫描时调用：如果订单已超时且还没结算过，就标记 overdue=1 + amount=0
     * + 累加快递员超时数（必要时封禁）。
     */
    private void tryMarkOverdue(Order order) {
        if (order.getDeadline() == null) return;
        if (order.getCourierId() == null) return;
        if (Integer.valueOf(1).equals(order.getOverdue())) return;
        if (LocalDateTime.now().isBefore(order.getDeadline())) return;

        int rows = baseMapper.markOverdue(order.getId());
        if (rows == 0) {
            // 已被别的路径（定时任务 / 完成事件）抢先标记过了，不重复结算
            return;
        }
        boolean banned = userFacade.recordCourierOverdue(
                order.getCourierId(), OVERDUE_BAN_THRESHOLD, BAN_DURATION_DAYS);
        log.info("订单超时结算: orderId={}, courierId={}, banned={}",
                order.getId(), order.getCourierId(), banned);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(Long orderId, Long userId) {
        Order order = getById(orderId);
        if (order == null) {
            throw new BusinessException(404, "order.not.found");
        }
        if (!order.getUserId().equals(userId)) {
            throw BusinessException.forbidden("order.cancel.only.owner");
        }
        int rows = baseMapper.updateStatusCAS(
                orderId,
                OrderStatus.PENDING.getCode(),
                OrderStatus.CANCELLED.getCode()
        );
        if (rows == 0) {
            throw new BusinessException(400, "order.cancel.not.allowed");
        }
        log.info("订单已取消: orderId={}, userId={}", orderId, userId);
    }

    /**
     * 定时任务入口：扫描"配送中且已过 deadline 但还没标记超时"的订单，
     * 对每一单执行结算（标 overdue + 清 amount + 累加快递员超时数）。
     * <p>
     * 不放在一个大事务里：每单独立结算，避免一单失败回滚整批。
     * 这种"批处理 + 单条幂等"的模式在工业界很常见。
     * </p>
     */
    @Override
    public int settleExpiredOrders() {
        List<OverdueScanRow> rows =
                baseMapper.scanOverdueOrders(OVERDUE_SCAN_BATCH);
        if (rows.isEmpty()) return 0;

        int settled = 0;
        for (OverdueScanRow row : rows) {
            try {
                int affected = baseMapper.markOverdue(row.getId());
                if (affected == 0) continue; // 被并发结算抢先了
                userFacade.recordCourierOverdue(
                        row.getCourierId(), OVERDUE_BAN_THRESHOLD, BAN_DURATION_DAYS);
                settled++;
            } catch (Exception e) {
                // 单条失败不影响整批，记日志继续
                log.error("超时结算单条失败: orderId={}", row.getId(), e);
            }
        }
        log.info("超时扫描完成: 候选{}单, 实际结算{}单", rows.size(), settled);
        return settled;
    }
}
