package com.uberclone.matching.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * SET NX PX-based distributed lock in Redis, used to guarantee
 * that at any moment only ONE ride request is outstanding to a given driver.
 * Auto-releases via TTL (the driver's acceptance window).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DistributedLock {

    private static final String KEY_PREFIX = "lock:driver:";
    private static final String TOKEN_KEY_PREFIX = "lock-token:driver:";

    private final StringRedisTemplate redis;

    public String tryLockDriver(String driverId, Duration ttl) {
        String token = UUID.randomUUID().toString();
        Boolean ok = redis.opsForValue().setIfAbsent(KEY_PREFIX + driverId, token, ttl);
        return Boolean.TRUE.equals(ok) ? token : null;
    }

    public boolean release(String driverId, String token) {
        String key = KEY_PREFIX + driverId;
        String current = redis.opsForValue().get(key);
        if (token.equals(current)) {
            redis.delete(key);
            return true;
        }
        return false;
    }

    public boolean isLocked(String driverId) {
        return Boolean.TRUE.equals(redis.hasKey(KEY_PREFIX + driverId));
    }

    /** Persist the lock token so a later (possibly-different) worker can release it. */
    public void rememberToken(String driverId, String token) {
        redis.opsForValue().set(TOKEN_KEY_PREFIX + driverId, token, Duration.ofMinutes(5));
    }

    public String recallToken(String driverId) {
        return redis.opsForValue().get(TOKEN_KEY_PREFIX + driverId);
    }

    public void forgetToken(String driverId) {
        redis.delete(TOKEN_KEY_PREFIX + driverId);
    }
}
