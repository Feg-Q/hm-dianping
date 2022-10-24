package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * @author Feg
 * @version 1.0
 * 使用 redis 实现分布式锁
 */
public class SimpleRedisLock implements ILock {

    private String name;
    private StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true);
    // 引入lua脚本操作
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("lua/unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    /**
     * @param timeoutSec key过期时间，在获取锁后如果redis宕机了，仍然可以在一段时间后释放锁
     * @return
     */
    @Override
    public boolean tryLock(Long timeoutSec) {
        long id = Thread.currentThread().getId();
        String value = ID_PREFIX + id;
        // 设置的value值包含一个随机UUID，和一个当前线程的id，保证完全不会重复
        Boolean lock = stringRedisTemplate.opsForValue().setIfAbsent(
                KEY_PREFIX + name, value, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(lock);
    }

    /**
     * 在释放锁之前判断当前的锁是否是自己的锁，避免释放了别人的锁
     */
//    @Override
//    public void unlock() {
//        long id = Thread.currentThread().getId();
//        String threadValue = ID_PREFIX + id;
//        // 先获取原本存在redis中的value值
//        String getValue = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
//        // 判断存的和当前线程的是否一致，如果一致就是自己的锁
//        if (threadValue.equals(getValue)) {
//            stringRedisTemplate.delete(KEY_PREFIX + name);
//        }
//    }

    /**
     * 现在使用lua脚本解决释放锁的两步 操作的原子性为问题
     */
    @Override
    public void unlock() {
        long id = Thread.currentThread().getId();
        String threadValue = ID_PREFIX + id;
        // 执行lua脚本
        stringRedisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(KEY_PREFIX + name), threadValue);
    }
}
