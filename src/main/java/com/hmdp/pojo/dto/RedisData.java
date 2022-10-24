package com.hmdp.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @author Feg
 * @version 1.0
 */
@Data
public class RedisData {

    private LocalDateTime expireTime;
    private Object data;

}
