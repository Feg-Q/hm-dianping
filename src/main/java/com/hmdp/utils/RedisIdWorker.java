package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * @author Feg
 * @version 1.0
 */
@Component
public class RedisIdWorker {

    private static final Long BEGIN_TIMESTAMP = 1640995200L;
    private static final int LEFT_BITS  = 32;

    private StringRedisTemplate stringRedisTemplate;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public Long nextId(String keyPrefix){
        // 1.生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowTime = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowTime - BEGIN_TIMESTAMP;
        // 2.生成序列号
        // 2.1 获取当天时间
        String today = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        // 2.2 获取自增长的值
        Long increment = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + today);
        // 3.拼接
        return timestamp << LEFT_BITS | increment;
    }
}
