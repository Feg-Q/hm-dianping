package com.hmdp.service.impl;

import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.common.RedisConstants;
import com.hmdp.pojo.dto.RedisData;
import com.hmdp.pojo.dto.Result;
import com.hmdp.pojo.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;



    @Override
    public Result queryShopById(Long id){
        // 缓存空值解决缓存穿透的问题(调用工具类)
//        Shop shop = cacheClient.queryWithPassThrough(RedisConstants.CACHE_SHOP_KEY,id,Shop.class,this::getById,RedisConstants.CACHE_SHOP_TTL,TimeUnit.MINUTES);

        // 互斥锁解决缓存击穿的问题
        //Shop shop = queryWithMutex(id);

        // 逻辑过期解决缓存击穿的问题
        Shop shop = cacheClient.queryWithLogicalExpire(
                RedisConstants.CACHE_SHOP_KEY,id,Shop.class,RedisConstants.LOCK_SHOP_KEY,
                this::getById,RedisConstants.LOCK_SHOP_TTL,TimeUnit.SECONDS);

        // 如果查出来的是空的，则返回报错
        if (shop == null){
            return Result.fail("该商户不存在");
        }
        return Result.ok(shop);
    }


    /**
     * 这种方法在视频里是完全没有问题的，1秒100并发，前几十个是旧数据，后面的是新数据
     * 但我测试的时候可能是请求发送的太快了，这些请求一下子都到了获取锁那里，
     * 然后一个线程获取到，其它的直接返回旧数据了，而这时候数据库还没有查完...
     * 这就导致所有的线程返回的都是旧数据，最后才把缓存更新好，
     * 直到我调到5秒200并发才勉强达到视频里的效果。
     * @param id
     * @return
     */
    // 使用逻辑过期来解决缓存击穿
//    public Shop queryWithExpireTime(Long id){
//        String shopKey = RedisConstants.CACHE_SHOP_KEY + id;
//        // 查缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);
//        // 如果缓存不存在，直接返回
//        if (StrUtil.isBlank(shopJson)){
//            return null;
//        }
//        // 缓存存在，则将缓存数据反序列化为对象
//        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
//        // 获取自己存入的过期时间
//        LocalDateTime expireTime = redisData.getExpireTime();
//        JSONObject data = (JSONObject) redisData.getData();
//        Shop shop = JSONUtil.toBean(data, Shop.class);
//        // 判断是否过期
//        if (expireTime.isAfter(LocalDateTime.now())){
//            // 未过期，返回数据
//            return shop;
//        }
//        // 过期了，先获取锁
//        String lockShopKey = RedisConstants.LOCK_SHOP_KEY + id;
//        boolean getLock = getLock(lockShopKey);
//        if (getLock){
//            // 获取到锁，要开启一个线程来查询数据库并保存redis
//            CACHE_REBUILD_EXECUTOR.submit(() -> {
//                try {
//                    saveShopToRedis(id,20L);
//                } catch (InterruptedException e) {
//                    throw new RuntimeException(e);
//                } finally {
//                    unLock(lockShopKey);
//                }
//            });
//        }
//        // 主线程先返回旧数据
//        return shop;
//    }

    // 使用互斥锁来解决缓存击穿问题
//    public Shop queryWithMutex(Long id){
//        String shopKey = RedisConstants.CACHE_SHOP_KEY + id;
//        // 查缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);
//        // 如果缓存存在
//        if (StrUtil.isNotBlank(shopJson)){
//            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
//            return shop;
//        }
//        // 判断是否是缓存的空值
//        if ("".equals(shopJson)){
//            return null;
//        }
//        Shop shopById = null;
//        // 获取锁
//        try {
//            boolean getLock = getLock(RedisConstants.LOCK_SHOP_KEY);
//            // 如果获取锁失败
//            if (!getLock){
//                Thread.sleep(50);
//                return queryWithMutex(id);
//            }
//            // 如果获取锁成功,就查数据库并保存缓存
//            // 获取锁成功后再查一次缓存，防止特殊情况
//            String shopJsonTwo = stringRedisTemplate.opsForValue().get(shopKey);
//            // 如果缓存存在,就转换后直接返回
//            if (StrUtil.isNotBlank(shopJson)){
//                return JSONUtil.toBean(shopJson, Shop.class);
//            }
//            // 缓存空值的情况
//            if ("".equals(shopJsonTwo)){
//                return null;
//            }
//            // 此时才开始真正地查数据库
//            shopById = this.getById(id);
//            // 休眠200毫秒，模拟查询数据库较慢的情况
//            Thread.sleep(200);
//            // 如果数据库也查不到
//            if (shopById == null){
//                // 将空值缓存进redis，防止缓存穿透
//                stringRedisTemplate.opsForValue().set(shopKey,"",RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
//                return null;
//            }
//            // 将数据库查出来的数据保存到缓存中，并设置过期时间
//            stringRedisTemplate.opsForValue().set(shopKey,JSONUtil.toJsonStr(shopById),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        } finally {
//            // 释放锁
//            unLock(RedisConstants.LOCK_SHOP_KEY);
//        }
//        // 返回结果
//        return shopById;
//    }

    // 解决缓存穿透的问题
//    public Shop queryWithPassThrough(Long id){
//        String shopKey = RedisConstants.CACHE_SHOP_KEY + id;
//        // 查缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);
//        // 如果缓存存在
//        if (StrUtil.isNotBlank(shopJson)){
//            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
//            return shop;
//        }
//        // 对于缓存穿透的解决方案，缓存空值
//        if (shopJson != null){
//            return null;
//        }
//        // 不存在就查数据库
//        Shop shopById = this.getById(id);
//        if (shopById == null){
//            // 将空值缓存进redis，防止缓存穿透
//            stringRedisTemplate.opsForValue().set(shopKey,"",RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
//            return null;
//        }
//        // 将数据库查出来的数据保存到缓存中
//        stringRedisTemplate.opsForValue().set(shopKey,JSONUtil.toJsonStr(shopById),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        return shopById;
//    }

//    public boolean getLock(String key){
//        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10L, TimeUnit.SECONDS);
//        return BooleanUtil.isTrue(flag);
//    }
//
//    public void unLock(String key){
//        stringRedisTemplate.delete(key);
//    }

    // 将一个商铺的信息加上过期时间保存在Redis
//    public void saveShopToRedis(Long id,Long expireSeconds) {
//        Shop shop = this.getById(id);
//        RedisData redisData = new RedisData();
//        redisData.setData(shop);
//        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
//        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(redisData));
//    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result updateShop(Shop shop) {
        Long id = shop.getId();
        if (id == null){
            return Result.fail("参数错误，id不能为空");
        }
        this.updateById(shop);
        String shopKey = RedisConstants.CACHE_SHOP_KEY + id;
        stringRedisTemplate.delete(shopKey);
        return Result.ok();
    }
}
