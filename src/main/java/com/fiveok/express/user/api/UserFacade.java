package com.fiveok.express.user.api;

import java.util.List;
import java.util.Map;

/**
 * 用户模块对外门面（Facade）
 * <p>
 * <b>这是 user 模块允许其他模块调用的唯一入口。</b>
 * order 模块只能 import this package，不能 import user.service / user.entity / user.mapper。
 * </p>
 * <p>
 * 通过这个约束，user 内部实现可以自由重构（改 mapper、改字段），
 * 只要 UserFacade 接口不变，order 模块就不会受影响——这就是"模块边界"的意义。
 * </p>
 */
public interface UserFacade {

    /**
     * 根据用户ID批量查询用户视图（用于订单列表带出用户信息）
     *
     * @param userIds 用户ID列表
     * @return userId -> UserView 映射
     */
    Map<Long, UserView> findByIds(List<Long> userIds);

    /**
     * 根据用户ID查询单个用户视图
     */
    UserView findById(Long userId);

    /**
     * 检查快递员是否被封禁（超时累计 5 次后会被封 7 天）。
     * <p>
     * 注意：实现侧用 SQL 直接判断 ban_until > NOW()，不依赖 JVM 时钟，
     * 避免多机部署时的时钟漂移。
     * </p>
     *
     * @param courierId 快递员 ID
     * @return true = 当前处于封禁中，不能抢单
     */
    boolean isCourierBanned(Long courierId);

    /**
     * 累加快递员超时次数；如果累加后达到阈值，自动设置 ban_until 并清零计数。
     * <p>
     * 关键约定：
     * <ul>
     *   <li><b>调用方负责幂等</b>：order 模块用 overdue=0→1 的 CAS 保证一次超时只调用一次</li>
     *   <li>本方法用 SQL 自增 + 触发封禁，无需事务嵌套</li>
     * </ul>
     * </p>
     *
     * @param courierId         超时的快递员 ID
     * @param threshold         触发封禁的阈值（推荐 5）
     * @param banDurationDays   封禁时长（推荐 7 天）
     * @return 累加后是否触发了封禁（true = 本次累加把该快递员封了）
     */
    boolean recordCourierOverdue(Long courierId, int threshold, int banDurationDays);
}
