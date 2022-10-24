---
--- Generated by EmmyLua(https://github.com/EmmyLua)
--- Created by 18270.
--- DateTime: 2022/10/22 16:50
--- 执行判断库存、判断是否已下单、减库存、添加订单功能
---
local voucherId = ARGV[1]
local userId = ARGV[2]

local stockKey = 'seckill:stock:' .. voucherId
local orderKey = 'seckill:order:' .. voucherId

if (tonumber(redis.call('get',stockKey)) <= 0) then
    return 1;
end
if (redis.call('sismember',orderKey,userId) == 1) then
    return 2;
end
redis.call('incrby',stockKey,-1)
redis.call('sadd',orderKey,userId)
return 0