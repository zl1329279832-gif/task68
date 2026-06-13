package com.service;

import com.baomidou.mybatisplus.service.IService;
import com.utils.PageUtils;
import com.utils.R;
import com.entity.ChangpianOrderEntity;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;

/**
 * 商品订单 服务类
 */
public interface ChangpianOrderService extends IService<ChangpianOrderEntity> {

    /**
    * @param params 查询参数
    * @return 带分页的查询出来的数据
    */
     PageUtils queryPage(Map<String, Object> params);

    /**
     * 下单（整段事务化，悲观锁防超卖）
     *
     * @param userId                 当前登录用户id
     * @param addressId              收货地址id
     * @param changpianOrderPaymentTypes 支付类型（1=余额 2=积分）
     * @param changpians             商品列表（每个元素包含 changpianId、buyNumber、id[购物车id]）
     * @param request                HttpServletRequest
     * @return R.ok() 或 R.error(...)
     */
     R placeOrder(Integer userId, Integer addressId, Integer changpianOrderPaymentTypes,
                  List<Map> changpians, HttpServletRequest request);

    /**
     * 退款（事务化：余额、积分、会员等级、库存、订单状态一起回滚）
     *
     * @param orderId 订单id
     * @param userId  当前登录用户id
     * @param request HttpServletRequest
     * @return R.ok() 或 R.error(...)
     */
     R refundOrder(Integer orderId, Integer userId, HttpServletRequest request);

    /**
     * 发货（仅允许"已支付/待发货"状态 -> 已发货）
     */
     R deliverOrder(Integer orderId, String courierNumber, String courierName);

    /**
     * 收货（仅允许"已发货"状态 -> 已收货）
     */
     R receiveOrder(Integer orderId);
}