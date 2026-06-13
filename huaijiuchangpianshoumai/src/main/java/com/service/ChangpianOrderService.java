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
     * 下单（事务化 + 悲观锁防超卖）
     */
     R placeOrder(Integer userId, Integer addressId, Integer changpianOrderPaymentTypes, List<Map<String, Object>> changpians);

    /**
     * 退款（余额、积分、会员等级、库存、订单状态一致性回滚）
     */
     R refundOrder(Integer orderId, Integer userId);
}