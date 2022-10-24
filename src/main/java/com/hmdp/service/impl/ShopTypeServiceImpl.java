package com.hmdp.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.common.RedisConstants;
import com.hmdp.pojo.dto.Result;
import com.hmdp.pojo.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 *  商品类型的服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeList() {
        List<String> list = stringRedisTemplate.opsForList().range(RedisConstants.CACHE_TYPE_KEY, 0, 10);
        // 如果查缓存的结果不为空
        if (CollectionUtil.isNotEmpty(list)) {
            // 使用流将每一项的string转换为ShopType对象，然后返回给前端
            List<ShopType> shopTypes = list.stream().map(s -> JSONUtil.toBean(s, ShopType.class)).collect(Collectors.toList());
            return Result.ok(shopTypes);
        }
        // 如果缓存里没有，就查数据库
        List<ShopType> shopTypes = this.list();
        // 将查出来的数据序列化后保存在redis中
        List<String> collect = shopTypes.stream().map(JSONUtil::toJsonStr).collect(Collectors.toList());
        stringRedisTemplate.opsForList().rightPushAll(RedisConstants.CACHE_TYPE_KEY, collect);
        return Result.ok(shopTypes);
    }
}
