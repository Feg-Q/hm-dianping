package com.hmdp.service;

import com.hmdp.pojo.dto.Result;
import com.hmdp.pojo.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {
    Result seckillVoucher(Long voucherId);

    Result creatVoucherOrder(Long voucherId);
    void creatVoucherOrder(VoucherOrder voucherOrder);
}
