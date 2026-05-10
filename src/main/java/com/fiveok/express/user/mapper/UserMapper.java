package com.fiveok.express.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fiveok.express.user.entity.User;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface UserMapper extends BaseMapper<User> {

    /**
     * 是否处于封禁中：ban_until > NOW()。
     * 用 SQL 直接判断，避免 JVM 时钟漂移。
     */
    @Select("SELECT COUNT(1) FROM users " +
            "WHERE id = #{courierId} AND deleted = 0 " +
            "AND ban_until IS NOT NULL AND ban_until > NOW()")
    int countBanned(@Param("courierId") Long courierId);

    /**
     * 累加超时计数（一个 SQL 内完成）。
     * <p>
     * 不会触发封禁本身——封禁逻辑由 service 层判断后单独 SQL 执行。
     * 这样可以读到本次累加后的真实计数。
     * </p>
     */
    @Update("UPDATE users SET overdue_count = overdue_count + 1 " +
            "WHERE id = #{courierId} AND deleted = 0")
    int incrementOverdueCount(@Param("courierId") Long courierId);

    /**
     * 触发封禁：把 overdue_count 清零，ban_until 设为 NOW()+banDays 天。
     * <p>
     * 用 CAS（overdue_count >= threshold）保证：即使并发情况下被多个超时事件同时
     * 推过阈值，也只会被封一次（第二次 CAS 找不到匹配行）。
     * </p>
     */
    @Update("UPDATE users SET overdue_count = 0, " +
            "ban_until = DATE_ADD(NOW(), INTERVAL #{banDays} DAY) " +
            "WHERE id = #{courierId} AND deleted = 0 " +
            "AND overdue_count >= #{threshold}")
    int triggerBanIfReached(@Param("courierId") Long courierId,
                            @Param("threshold") int threshold,
                            @Param("banDays") int banDays);
}
