package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.common.RedisConstants;
import com.hmdp.pojo.dto.RedisData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * @author Feg
 * @version 1.0
 * 实现缓存并解决了缓存三个问题的工具类
 */
@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    // 通过构造函数注入redis客户端
    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    // 将一个value值缓存进redis，有实际过期时间
    public void set(String key, Object value, Long time, TimeUnit timeUnit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,timeUnit);
    }

    // 解决缓存穿透的通用方案
    public <R,I> R queryWithPassThrough(
            String prefixKey, I id, Class<R> type, Function<I,R> dbFallBack, Long time, TimeUnit timeUnit){
        // 拼接缓存key
        String key = prefixKey + id;
        // 查缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 如果缓存存在
        if (StrUtil.isNotBlank(json)){
            return JSONUtil.toBean(json, type);
        }
        // 对于缓存穿透的解决方案，缓存空值
        if (json != null){
            return null;
        }
        // 不存在就查数据库
        R r = dbFallBack.apply(id);
        if (r == null){
            // 将空值缓存进redis，防止缓存穿透
            stringRedisTemplate.opsForValue().set(key,"",RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        // 将数据库查出来的数据保存到缓存中
        this.set(key, r, time, timeUnit);
        return r;
    }

    // 使用逻辑过期来解决缓存击穿
    public <R,I> R queryWithLogicalExpire(
            String keyPrefix, I id, Class<R> type, String lockPrefix, Function<I,R> dbFallBack, Long time, TimeUnit timeUnit){
        String key = keyPrefix + id;
        // 查缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 如果缓存不存在，直接返回
        if (StrUtil.isBlank(json)){
            return null;
        }
        // 缓存存在，则将缓存数据反序列化为对象
        com.hmdp.pojo.dto.RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        // 获取自己存入的过期时间
        LocalDateTime expireTime = redisData.getExpireTime();
        JSONObject data = (JSONObject) redisData.getData();
        R r = JSONUtil.toBean(data, type);
        // 判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())){
            // 未过期，返回数据
            return r;
        }
        // 过期了，先获取锁
        String lockKey = lockPrefix + id;
        boolean getLock = getLock(lockKey);
        if (getLock){
            // 获取到锁，要开启一个线程来查询数据库并保存redis
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    R r1 = dbFallBack.apply(id);
                    this.setWithLogicalExpire(key,r1,time,timeUnit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unLock(lockKey);
                }
            });
        }
        // 主线程先返回旧数据
        return r;
    }

    public boolean getLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10L, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    public void unLock(String key){
        stringRedisTemplate.delete(key);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit timeUnit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(time));
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));
    }
}
