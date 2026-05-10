package com.fiveok.express.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fiveok.express.order.entity.Order;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface OrderMapper extends BaseMapper<Order> {

    /**
     * 抢单原子 SQL：利用 WHERE status=0 + InnoDB 行锁保证并发安全。
     * 同时把 grab_time = NOW()，deadline = NOW() + promised_minutes 一并写入。
     */
    @Update("UPDATE orders " +
            "SET status = 1, " +
            "    courier_id = #{courierId}, " +
            "    promised_minutes = #{promisedMinutes}, " +
            "    grab_time = NOW(), " +
            "    deadline = DATE_ADD(NOW(), INTERVAL #{promisedMinutes} MINUTE), " +
            "    update_time = NOW() " +
            "WHERE id = #{orderId} AND status = 0 AND deleted = 0")
    int grabOrder(@Param("orderId") Long orderId,
                  @Param("courierId") Long courierId,
                  @Param("promisedMinutes") Integer promisedMinutes);

    /**
     * 状态流转 CAS：基于 oldStatus 条件更新，防止并发覆盖。
     */
    @Update("UPDATE orders SET status = #{newStatus}, update_time = NOW() " +
            "WHERE id = #{orderId} AND status = #{oldStatus} AND deleted = 0")
    int updateStatusCAS(@Param("orderId") Long orderId,
                        @Param("oldStatus") Integer oldStatus,
                        @Param("newStatus") Integer newStatus);

    /**
     * 标记超时：CAS overdue=0 → 1，同时把 amount 清零（用户免单）。
     * <p>
     * <b>这是整个超时机制的关键</b>：CAS 保证一个订单只会被标记一次，
     * 调用方只有在 rows==1 时才能去累加快递员的超时次数，避免重复扣分。
     * </p>
     * <p>
     * 不更新 status：超时和"完成/进行中"是正交的两个维度。
     * 一个订单可以是"配送中且已超时"，也可以是"已完成但本次超时所以免单"。
     * </p>
     */
    @Update("UPDATE orders SET overdue = 1, amount = 0, update_time = NOW() " +
            "WHERE id = #{orderId} AND overdue = 0 AND deleted = 0")
    int markOverdue(@Param("orderId") Long orderId);

    /**
     * 扫描所有应该被标记超时但还没标的订单：
     * status=配送中(1) + overdue=0 + deadline<NOW()。
     * 用 LIMIT 限制单批数量，防止积压时一次拉太多内存炸了。
     */
    @Select("SELECT id, courier_id FROM orders " +
            "WHERE status = 1 AND overdue = 0 AND deadline IS NOT NULL " +
            "AND deadline < NOW() AND deleted = 0 " +
            "ORDER BY deadline ASC LIMIT #{limit}")
    List<OverdueScanRow> scanOverdueOrders(@Param("limit") int limit);
}
