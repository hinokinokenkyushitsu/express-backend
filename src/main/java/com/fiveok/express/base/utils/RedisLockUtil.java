package com.fiveok.express.base.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Redis 分布式锁工具（共享组件）
 */
@Component
@RequiredArgsConstructor
public class RedisLockUtil {

    private final StringRedisTemplate redisTemplate;

    private static final String LOCK_PREFIX = "lock:order:";
    private static final long DEFAULT_EXPIRE_SECONDS = 5;

    public boolean tryLock(Long orderId) {
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(LOCK_PREFIX + orderId, "1", DEFAULT_EXPIRE_SECONDS, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    public void unlock(Long orderId) {
        redisTemplate.delete(LOCK_PREFIX + orderId);
    }
}
