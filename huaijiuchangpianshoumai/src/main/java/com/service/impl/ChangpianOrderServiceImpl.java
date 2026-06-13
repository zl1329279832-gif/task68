package com.service.impl;

import com.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.*;
import com.baomidou.mybatisplus.mapper.EntityWrapper;
import com.baomidou.mybatisplus.mapper.Wrapper;
import com.baomidou.mybatisplus.plugins.Page;
import com.baomidou.mybatisplus.service.impl.ServiceImpl;
import org.springframework.transaction.annotation.Transactional;
import com.utils.PageUtils;
import com.utils.Query;
import com.utils.R;
import org.springframework.web.context.ContextLoader;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import com.dao.ChangpianDao;
import com.dao.ChangpianOrderDao;
import com.entity.ChangpianEntity;
import com.entity.ChangpianOrderEntity;
import com.entity.DictionaryEntity;
import com.entity.YonghuEntity;
import com.service.*;
import com.entity.view.ChangpianOrderView;

/**
 * 商品订单 服务实现类
 */
@Service("changpianOrderService")
@Transactional
public class ChangpianOrderServiceImpl extends ServiceImpl<ChangpianOrderDao, ChangpianOrderEntity> implements ChangpianOrderService {

    private static final Logger logger = LoggerFactory.getLogger(ChangpianOrderServiceImpl.class);

    /** 订单状态常量 */
    private static final int STATUS_YI_PINGJIA = 1;   // 已评价
    private static final int STATUS_TUIKUAN    = 2;   // 退款
    private static final int STATUS_YI_ZHIFU   = 3;   // 已支付（待发货）
    private static final int STATUS_YI_FAHUO   = 4;   // 已发货
    private static final int STATUS_YI_SHOUHUO = 5;   // 已收货

    @Autowired
    private ChangpianDao changpianDao;

    @Autowired
    private YonghuService yonghuService;

    @Autowired
    private CartService cartService;

    @Autowired
    private DictionaryService dictionaryService;

    @Override
    public PageUtils queryPage(Map<String,Object> params) {
        if(params != null && (params.get("limit") == null || params.get("page") == null)){
            params.put("page","1");
            params.put("limit","10");
        }
        Page<ChangpianOrderView> page =new Query<ChangpianOrderView>(params).getPage();
        page.setRecords(baseMapper.selectListView(page,params));
        return new PageUtils(page);
    }


    /**
     * 下单 —— 整段事务化，悲观锁防超卖
     * 由于 AOP 事务切面已配置在 com.service..* 上且 propagation=REQUIRED，
     * 本方法内调用的所有 service 方法（yonghuService、cartService 等）都加入同一事务。
     */
    @Override
    public R placeOrder(Integer userId, Integer addressId, Integer changpianOrderPaymentTypes,
                        List<Map> changpians, HttpServletRequest request) {

        // 生成统一订单号
        String changpianOrderUuidNumber = String.valueOf(new Date().getTime());

        // 获取用户信息
        YonghuEntity yonghuEntity = yonghuService.selectById(userId);
        if (yonghuEntity == null) {
            return R.error(511, "用户不存在");
        }

        // 获取折扣（从字典表）
        BigDecimal zhekou = new BigDecimal(1.0);
        Wrapper<DictionaryEntity> dictionaryWrapper = new EntityWrapper<DictionaryEntity>()
                .eq("dic_code", "huiyuandengji_types")
                .eq("dic_name", "会员等级类型")
                .eq("code_index", yonghuEntity.getHuiyuandengjiTypes());
        DictionaryEntity dictionaryEntity = dictionaryService.selectOne(dictionaryWrapper);
        if (dictionaryEntity != null) {
            zhekou = BigDecimal.valueOf(Double.valueOf(dictionaryEntity.getBeizhu()));
        }

        // 用于收集要批量插入的订单和要更新的商品
        List<ChangpianOrderEntity> changpianOrderList = new ArrayList<ChangpianOrderEntity>();
        List<ChangpianEntity> changpianList = new ArrayList<ChangpianEntity>();
        List<Integer> cartIds = new ArrayList<Integer>();

        // 累计本次订单的总消费金额和总获得积分（先计算完再统一扣减，避免中间态）
        double totalMoney = 0.0;
        double totalBuyJifen = 0.0;

        // ---- 第一轮：悲观锁逐个检查库存，收集数据 ----
        for (Map<String, Object> map : changpians) {
            Integer changpianId = Integer.valueOf(String.valueOf(map.get("changpianId")));
            Integer buyNumber = Integer.valueOf(String.valueOf(map.get("buyNumber")));
            String cartIdStr = String.valueOf(map.get("id"));
            if (StringUtil.isNotEmpty(cartIdStr) && !"null".equals(cartIdStr)) {
                cartIds.add(Integer.valueOf(cartIdStr));
            }

            // ★ 悲观锁：SELECT ... FOR UPDATE，防止并发超卖
            ChangpianEntity changpianEntity = changpianDao.selectForUpdate(changpianId);
            if (changpianEntity == null) {
                return R.error(511, "商品id=" + changpianId + "不存在");
            }
            if (changpianEntity.getChangpianNewMoney() == null) {
                return R.error(511, "商品【" + changpianEntity.getChangpianName() + "】价格不能为空");
            }
            if (changpianEntity.getChangpianKucunNumber() < buyNumber) {
                return R.error("【" + changpianEntity.getChangpianName() + "】库存不足，当前库存：" + changpianEntity.getChangpianKucunNumber());
            }

            // 扣减库存（在锁内操作，其他并发事务会等待锁释放后看到最新值）
            changpianEntity.setChangpianKucunNumber(changpianEntity.getChangpianKucunNumber() - buyNumber);
            changpianList.add(changpianEntity);

            // 计算本行金额和积分
            if (changpianOrderPaymentTypes == 1) { // 余额支付
                double lineMoney = new BigDecimal(changpianEntity.getChangpianNewMoney())
                        .multiply(new BigDecimal(buyNumber))
                        .multiply(zhekou).doubleValue();
                totalMoney += lineMoney;

                double lineJifen = new BigDecimal(changpianEntity.getChangpianPrice())
                        .multiply(new BigDecimal(buyNumber)).doubleValue();
                totalBuyJifen += lineJifen;
            }

            // 构造订单实体
            ChangpianOrderEntity orderEntity = new ChangpianOrderEntity();
            orderEntity.setChangpianOrderUuidNumber(changpianOrderUuidNumber);
            orderEntity.setAddressId(addressId);
            orderEntity.setChangpianId(changpianId);
            orderEntity.setYonghuId(userId);
            orderEntity.setBuyNumber(buyNumber);
            orderEntity.setChangpianOrderTypes(STATUS_YI_ZHIFU);
            orderEntity.setChangpianOrderPaymentTypes(changpianOrderPaymentTypes);
            orderEntity.setInsertTime(new Date());
            orderEntity.setCreateTime(new Date());

            if (changpianOrderPaymentTypes == 1) {
                double lineMoney = new BigDecimal(changpianEntity.getChangpianNewMoney())
                        .multiply(new BigDecimal(buyNumber))
                        .multiply(zhekou).doubleValue();
                orderEntity.setChangpianOrderTruePrice(lineMoney);
            }

            changpianOrderList.add(orderEntity);
        }

        // ---- 第二轮：校验余额（所有商品都检查过库存后统一校验） ----
        if (changpianOrderPaymentTypes == 1) {
            if (yonghuEntity.getNewMoney() == null || yonghuEntity.getNewMoney() - totalMoney < 0) {
                return R.error("余额不足,请充值！！！");
            }
        }

        // ---- 第三轮：统一更新用户余额、积分、会员等级 ----
        if (changpianOrderPaymentTypes == 1) {
            yonghuEntity.setNewMoney(yonghuEntity.getNewMoney() - totalMoney);
            yonghuEntity.setYonghuSumJifen(yonghuEntity.getYonghuSumJifen() + totalBuyJifen);
            // 使用可配置的会员等级阈值
            yonghuEntity.setHuiyuandengjiTypes(
                    dictionaryService.calculateMembershipTier(yonghuEntity.getYonghuSumJifen()));
        }

        // ---- 第四轮：批量持久化（全部在同一事务内） ----
        this.insertBatch(changpianOrderList);
        for (ChangpianEntity cp : changpianList) {
            // 使用 updateById 逐个更新，确保悲观锁行的变更被正确提交
            // （由于 changpianService 和当前 service 共享事务，这里也可以注入 changpianService）
            changpianDao.updateById(cp);
        }
        yonghuService.updateById(yonghuEntity);

        // 清理购物车
        if (cartIds != null && cartIds.size() > 0) {
            cartService.deleteBatchIds(cartIds);
        }

        return R.ok();
    }


    /**
     * 退款 —— 事务化回滚：余额、积分、会员等级、库存、订单状态
     */
    @Override
    public R refundOrder(Integer orderId, Integer userId, HttpServletRequest request) {
        // 1. 查订单
        ChangpianOrderEntity changpianOrder = this.selectById(orderId);
        if (changpianOrder == null) {
            return R.error(511, "订单不存在");
        }

        // 2. 状态校验：只有"已支付(3)"或"已发货(4)"允许退款（业务可按需收紧为仅已支付）
        int currentStatus = changpianOrder.getChangpianOrderTypes();
        if (currentStatus != STATUS_YI_ZHIFU && currentStatus != STATUS_YI_FAHUO) {
            return R.error(511, "当前订单状态不允许退款，只有已支付或已发货状态可以退款");
        }

        Integer buyNumber = changpianOrder.getBuyNumber();
        Integer changpianOrderPaymentTypes = changpianOrder.getChangpianOrderPaymentTypes();
        Integer changpianId = changpianOrder.getChangpianId();
        if (changpianId == null) {
            return R.error(511, "订单缺少商品信息");
        }

        // 3. 悲观锁查商品（防止并发退款导致库存多加）
        ChangpianEntity changpianEntity = changpianDao.selectForUpdate(changpianId);
        if (changpianEntity == null) {
            return R.error(511, "商品不存在");
        }
        if (changpianEntity.getChangpianNewMoney() == null) {
            return R.error(511, "商品价格不能为空");
        }

        // 4. 查用户
        YonghuEntity yonghuEntity = yonghuService.selectById(userId);
        if (yonghuEntity == null) {
            return R.error(511, "用户不存在");
        }

        // 5. 获取折扣
        double zhekou = 1.0;
        Wrapper<DictionaryEntity> dictionaryWrapper = new EntityWrapper<DictionaryEntity>()
                .eq("dic_code", "huiyuandengji_types")
                .eq("dic_name", "会员等级类型")
                .eq("code_index", yonghuEntity.getHuiyuandengjiTypes());
        DictionaryEntity dictionaryEntity = dictionaryService.selectOne(dictionaryWrapper);
        if (dictionaryEntity != null) {
            zhekou = Double.valueOf(dictionaryEntity.getBeizhu());
        }

        // 6. 回滚余额、积分、会员等级（仅余额支付时）
        if (changpianOrderPaymentTypes == 1) {
            if (yonghuEntity.getNewMoney() == null) {
                return R.error(511, "用户金额不能为空");
            }
            // 退回金额
            double refundMoney = new BigDecimal(changpianEntity.getChangpianNewMoney())
                    .multiply(new BigDecimal(buyNumber))
                    .multiply(BigDecimal.valueOf(zhekou)).doubleValue();
            yonghuEntity.setNewMoney(yonghuEntity.getNewMoney() + refundMoney);

            // 扣减积分
            double buyJifen = new BigDecimal(changpianEntity.getChangpianPrice())
                    .multiply(new BigDecimal(buyNumber)).doubleValue();
            double newSumJifen = yonghuEntity.getYonghuSumJifen() - buyJifen;
            if (newSumJifen < 0) {
                newSumJifen = 0;
            }
            yonghuEntity.setYonghuSumJifen(newSumJifen);

            // 重新计算会员等级（使用可配置阈值）
            yonghuEntity.setHuiyuandengjiTypes(
                    dictionaryService.calculateMembershipTier(newSumJifen));
        }

        // 7. 回滚库存
        changpianEntity.setChangpianKucunNumber(changpianEntity.getChangpianKucunNumber() + buyNumber);

        // 8. 更新订单状态为退款
        changpianOrder.setChangpianOrderTypes(STATUS_TUIKUAN);

        // 9. 持久化（全部在同一事务内）
        this.updateById(changpianOrder);
        changpianDao.updateById(changpianEntity);
        yonghuService.updateById(yonghuEntity);

        return R.ok();
    }


    /**
     * 发货 —— 仅允许"已支付/待发货(3)" -> "已发货(4)"
     */
    @Override
    public R deliverOrder(Integer orderId, String courierNumber, String courierName) {
        ChangpianOrderEntity changpianOrder = this.selectById(orderId);
        if (changpianOrder == null) {
            return R.error(511, "订单不存在");
        }
        if (changpianOrder.getChangpianOrderTypes() != STATUS_YI_ZHIFU) {
            return R.error(511, "当前订单状态不允许发货，只有已支付（待发货）状态可以发货");
        }

        ChangpianOrderEntity updateEntity = new ChangpianOrderEntity();
        updateEntity.setId(orderId);
        updateEntity.setChangpianOrderTypes(STATUS_YI_FAHUO);
        updateEntity.setChangpianOrderCourierNumber(courierNumber);
        updateEntity.setChangpianOrderCourierName(courierName);
        boolean b = this.updateById(updateEntity);
        if (!b) {
            return R.error("发货出错");
        }
        return R.ok();
    }


    /**
     * 收货 —— 仅允许"已发货(4)" -> "已收货(5)"
     */
    @Override
    public R receiveOrder(Integer orderId) {
        ChangpianOrderEntity changpianOrder = this.selectById(orderId);
        if (changpianOrder == null) {
            return R.error(511, "订单不存在");
        }
        if (changpianOrder.getChangpianOrderTypes() != STATUS_YI_FAHUO) {
            return R.error(511, "当前订单状态不允许收货，只有已发货状态可以收货");
        }

        ChangpianOrderEntity updateEntity = new ChangpianOrderEntity();
        updateEntity.setId(orderId);
        updateEntity.setChangpianOrderTypes(STATUS_YI_SHOUHUO);
        boolean b = this.updateById(updateEntity);
        if (!b) {
            return R.error("收货出错");
        }
        return R.ok();
    }

}
