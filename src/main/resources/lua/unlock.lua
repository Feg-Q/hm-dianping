---
--- Generated by EmmyLua(https://github.com/EmmyLua)
--- Created by 18270.
--- DateTime: 2022/10/21 13:35
---
if(redis.call('get',KEYS[1]) == ARGV[1]) then
    return redis.call('del',KEYS[1])
end
return 0