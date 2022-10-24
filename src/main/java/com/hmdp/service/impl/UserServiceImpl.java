package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.common.SystemConstants;
import com.hmdp.pojo.dto.LoginFormDTO;
import com.hmdp.pojo.dto.Result;
import com.hmdp.pojo.dto.UserDTO;
import com.hmdp.pojo.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.common.RedisConstants.*;

/**
 *
 * @author Feg
 * @since
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone) {
        if (RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式错误");
        }
        String code = RandomUtil.randomNumbers(6);
//        session.setAttribute("code"+phone,code);
        // 将手机号和code保存在redis中
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);
        log.info("验证码发送成功"+code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 校验手机号
        String loginFormPhone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(loginFormPhone)){
            return Result.fail("手机号格式错误");
        }
        // 校验验证码
        String formCode = loginForm.getCode();
        String saveCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY+loginFormPhone);
        if (formCode == null || !formCode.equals(saveCode)){
            return Result.fail("验证码错误");
        }
        // 从数据库查询这个手机号对应的用户
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("phone",loginFormPhone);
        User user = this.getOne(queryWrapper);
        // 如果不存在，就创建一个并保存在数据库
        if (user == null){
            user = new User();
            user.setPhone(loginFormPhone);
            // 使用随机的字符串加上一个固定的前缀来作为默认昵称
            user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX +RandomUtil.randomString(10));
            this.save(user);
        }
        String token = UUID.randomUUID().toString(true);
        // 将用户信息脱敏后保存在redis
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        // 把用户对象转换成Map便于存储到redis
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create().ignoreNullValue().setFieldValueEditor((fieldName,fieldValue) -> fieldValue.toString()));
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey,userMap);
        // 设置过期时间
        stringRedisTemplate.expire(tokenKey,LOGIN_USER_TTL,TimeUnit.MINUTES);
        return Result.ok(token);
    }
}
