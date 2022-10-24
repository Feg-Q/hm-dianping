package com.hmdp.service.impl;

import com.hmdp.pojo.dto.Result;
import com.hmdp.pojo.entity.SeckillVoucher;
import com.hmdp.pojo.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 *
 * @author Feg
 * @since 2022-10-20
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;

    // 阻塞队列
    BlockingQueue<VoucherOrder> queue = new ArrayBlockingQueue<>(1024*1024);
    // lua脚本
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("lua/seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    //线程池，单线程的，用来异步调用修改数据库的操作
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    // 异步下单修改数据库的操作需要在项目启动的时候就开始进行，以便等待信息，因此使用注解在类加载后执行这个方法
    @PostConstruct
    private void init(){
        // 调用线程池，提交异步下单的任务
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    // 异步下单的任务
    class VoucherOrderHandler implements Runnable{
        @Override
        public void run() {
            while (true){
                try {
                    // 从阻塞队列中取出数据，如果没有，线程会一直阻塞在这里
                    VoucherOrder voucherOrder = queue.take();
                    // 取出数据后执行具体的操作
                    handleVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.info("处理订单异常",e);
                }
            }
        }
    }
    // 异步下单的具体操作
    public void handleVoucherOrder(VoucherOrder voucherOrder){
        Long userId = voucherOrder.getUserId();
        // 使用Redisson的分布式锁
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        // 获取锁
        boolean tryLock = lock.tryLock();
        if (!tryLock){
            log.info("不允许重复下单");
            return;
        }
        // 获取成功执行修改数据库的操作
        try {
            proxy.creatVoucherOrder(voucherOrder);
        } finally {
            // 最终释放锁
            lock.unlock();
        }
    }
    // 将代理对象放到局部变量的位置，便于主线程之外的线程使用
    private IVoucherOrderService proxy;

    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        // 执行脚本
        Long executeResult = stringRedisTemplate.execute(SECKILL_SCRIPT, Collections.emptyList(), voucherId, userId);
        // 将返回值转为int类型
        int intValue = executeResult.intValue();
        // 返回值为1的情况
        if (intValue == 1){
            return Result.fail("库存不足");
        }
        // 返回值为2的情况
        if (intValue == 2){
            return Result.fail("一人只能下一单");
        }
        // lua脚本返回0，表示可以抢购订单了
        // 创建订单信息，并保存到阻塞队列中
        VoucherOrder voucherOrder = new VoucherOrder();
        Long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        queue.add(voucherOrder);
        // 在当前的主线程给代理对象赋值，因为在新开的线程中要使用
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        // 返回给用户响应
        return Result.ok(orderId);
    }

//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        // 1.查询优惠券信息
//        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
//        // 2.判断秒杀开始结束时间
//        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            return Result.fail("秒杀还未开始");
//        }
//        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
//            return Result.fail("秒杀已经结束");
//        }
//        // 3.判断库存，小于1即为0则无法再减
//        if (seckillVoucher.getStock() < 1) {
//            return Result.fail("库存不足");
//        }
//        // 获取存在ThreadLocal的用户的id
//        Long userId = UserHolder.getUser().getId();
//
//        // 锁加在事务前，防止事务还未提交就释放锁时的安全问题
//        // 使用当前用户的id的字符串的字面量来作为锁关键字
////        synchronized (userId.toString().intern()) {
////            // 获取当前类的事务的代理类，直接用this调用事务会失效，因为事务是基于代理类实现的
////            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
////            return proxy.creatVoucherOrder(voucherId);
////        }
//
//        // 上面的使用sync关键字解决的多线程问题在集群模式下失效，因此使用自己写的redis分布式锁
//        //SimpleRedisLock lock = new SimpleRedisLock("order" + userId, stringRedisTemplate);
//        // 使用Redisson的分布式锁
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
//
//        boolean tryLock = lock.tryLock();
//        if (!tryLock){
//            return Result.fail("一人只能下一单");
//        }
//        try {
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.creatVoucherOrder(voucherId);
//        } finally {
//            lock.unlock();
//        }
//    }

    // 同步情况下需要调用的修改数据库的操作
    @Transactional
    public Result creatVoucherOrder(Long voucherId){
        // 一人一单
        Long userId = UserHolder.getUser().getId();
        // 根据用户id和商品id从数据库查询
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0){
            return Result.fail("您已经下过单了");
        }
        // 4.更新库存，使用了优化的乐观锁，即在修改数据时判断数据是否满足要求（大于0）
        boolean updateResult = seckillVoucherService.update().setSql("stock = stock - 1")
                .eq("voucher_id", voucherId).gt("stock",0)
                .update();
        if (!updateResult){
            return Result.fail("库存不足");
        }
        // 5.生成订单
        VoucherOrder voucherOrder = new VoucherOrder();
        Long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        // 6.将订单保存在数据库中
        this.save(voucherOrder);
        return Result.ok(orderId);
    }

    // 异步下单需要调用的保存数据库的方法
    @Transactional
    public void creatVoucherOrder(VoucherOrder voucherOrder){
        // 一人一单
        Long userId = voucherOrder.getUserId();
        // 根据用户id和商品id从数据库查询
        int count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
        if (count > 0){
            log.error("您已经下过单了");
            return;
        }
        // 4.更新库存，使用了优化的乐观锁，即在修改数据时判断数据是否满足要求（大于0）
        boolean updateResult = seckillVoucherService.update().setSql("stock = stock - 1")
                .eq("voucher_id", voucherOrder.getVoucherId()).gt("stock",0)
                .update();
        if (!updateResult){
            log.error("库存不足");
            return;
        }
        // 6.将订单保存在数据库中
        this.save(voucherOrder);
    }
}
