package com.service.impl;

import com.utils.StringUtil;
import org.springframework.stereotype.Service;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.*;
import com.baomidou.mybatisplus.mapper.EntityWrapper;
import com.baomidou.mybatisplus.mapper.Wrapper;
import com.baomidou.mybatisplus.plugins.Page;
import com.baomidou.mybatisplus.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
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
import com.service.ChangpianOrderService;
import com.service.ChangpianService;
import com.service.CartService;
import com.service.DictionaryService;
import com.service.YonghuService;
import com.entity.view.ChangpianOrderView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 商品订单 服务实现类
 */
@Service("changpianOrderService")
@Transactional
public class ChangpianOrderServiceImpl extends ServiceImpl<ChangpianOrderDao, ChangpianOrderEntity> implements ChangpianOrderService {

    private static final Logger logger = LoggerFactory.getLogger(ChangpianOrderServiceImpl.class);

    @Autowired
    private ChangpianDao changpianDao;
    @Autowired
    private ChangpianService changpianService;
    @Autowired
    private YonghuService yonghuService;
    @Autowired
    private DictionaryService dictionaryService;
    @Autowired
    private CartService cartService;

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
     * 下单 —— 整段事务化 + SELECT ... FOR UPDATE 悲观锁防超卖
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public R placeOrder(Integer userId, Integer addressId, Integer changpianOrderPaymentTypes, List<Map<String, Object>> changpians) {
        String changpianOrderUuidNumber = String.valueOf(new Date().getTime());

        // 获取当前用户（在事务内）
        YonghuEntity yonghuEntity = yonghuService.selectById(userId);
        if (yonghuEntity == null) {
            return R.error(511, "用户不存在");
        }

        // 获取会员折扣
        BigDecimal zhekou = new BigDecimal(1.0);
        Wrapper<DictionaryEntity> dictionary = new EntityWrapper<DictionaryEntity>()
                .eq("dic_code", "huiyuandengji_types")
                .eq("dic_name", "会员等级类型")
                .eq("code_index", yonghuEntity.getHuiyuandengjiTypes());
        DictionaryEntity dictionaryEntity = dictionaryService.selectOne(dictionary);
        if (dictionaryEntity != null) {
            zhekou = BigDecimal.valueOf(Double.valueOf(dictionaryEntity.getBeizhu()));
        }

        List<ChangpianOrderEntity> changpianOrderList = new ArrayList<>();
        List<ChangpianEntity> changpianList = new ArrayList<>();
        List<Integer> cartIds = new ArrayList<>();

        for (Map<String, Object> map : changpians) {
            Integer changpianId = Integer.valueOf(String.valueOf(map.get("changpianId")));
            Integer buyNumber = Integer.valueOf(String.valueOf(map.get("buyNumber")));
            String id = String.valueOf(map.get("id"));
            if (StringUtil.isNotEmpty(id))
                cartIds.add(Integer.valueOf(id));

            // ★ 悲观锁：SELECT ... FOR UPDATE，同一商品的并发下单在此串行化
            ChangpianEntity changpianEntity = changpianDao.selectByIdForUpdate(changpianId);
            if (changpianEntity == null) {
                throw new RuntimeException("商品不存在，id=" + changpianId);
            }

            // 在锁内检查库存
            if (changpianEntity.getChangpianKucunNumber() < buyNumber) {
                throw new RuntimeException(changpianEntity.getChangpianName() + "的库存不足");
            }
            // 扣减库存
            changpianEntity.setChangpianKucunNumber(changpianEntity.getChangpianKucunNumber() - buyNumber);

            // 构建订单
            ChangpianOrderEntity changpianOrderEntity = new ChangpianOrderEntity<>();
            changpianOrderEntity.setChangpianOrderUuidNumber(changpianOrderUuidNumber);
            changpianOrderEntity.setAddressId(addressId);
            changpianOrderEntity.setChangpianId(changpianId);
            changpianOrderEntity.setYonghuId(userId);
            changpianOrderEntity.setBuyNumber(buyNumber);
            changpianOrderEntity.setChangpianOrderTypes(3); // 已支付
            changpianOrderEntity.setChangpianOrderPaymentTypes(changpianOrderPaymentTypes);
            changpianOrderEntity.setInsertTime(new Date());
            changpianOrderEntity.setCreateTime(new Date());

            // 余额支付
            if (changpianOrderPaymentTypes == 1) {
                Double money = new BigDecimal(changpianEntity.getChangpianNewMoney())
                        .multiply(new BigDecimal(buyNumber))
                        .multiply(zhekou).doubleValue();

                if (yonghuEntity.getNewMoney() - money < 0) {
                    throw new RuntimeException("余额不足,请充值！！！");
                }

                // 积分累加
                Double buyJifen = new BigDecimal(changpianEntity.getChangpianPrice())
                        .multiply(new BigDecimal(buyNumber)).doubleValue();
                yonghuEntity.setYonghuSumJifen(yonghuEntity.getYonghuSumJifen() + buyJifen);

                // 会员等级（从 DictionaryService 可配置阈值）
                yonghuEntity.setHuiyuandengjiTypes(dictionaryService.computeHuiyuandengjiLevel(yonghuEntity.getYonghuSumJifen()));

                changpianOrderEntity.setChangpianOrderTruePrice(money);
                yonghuEntity.setNewMoney(yonghuEntity.getNewMoney() - money);
            }

            changpianOrderList.add(changpianOrderEntity);
            changpianList.add(changpianEntity);
        }

        // 批量写入（在同一事务内）
        this.insertBatch(changpianOrderList);
        changpianService.updateBatchById(changpianList);
        yonghuService.updateById(yonghuEntity);
        if (cartIds != null && cartIds.size() > 0)
            cartService.deleteBatchIds(cartIds);

        return R.ok();
    }

    /**
     * 退款 —— 余额、积分、会员等级、库存、changpianOrderTypes 一致性回滚
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public R refundOrder(Integer orderId, Integer userId) {
        ChangpianOrderEntity changpianOrder = this.selectById(orderId);
        if (changpianOrder == null) {
            return R.error(511, "查不到该订单");
        }

        // ★ 状态前置校验：只有已支付(3)、已发货(4)、已收货(5) 可退款
        Integer currentStatus = changpianOrder.getChangpianOrderTypes();
        if (currentStatus == null || (currentStatus != 3 && currentStatus != 4 && currentStatus != 5)) {
            return R.error(511, "当前订单状态不允许退款");
        }

        Integer buyNumber = changpianOrder.getBuyNumber();
        Integer changpianOrderPaymentTypes = changpianOrder.getChangpianOrderPaymentTypes();
        Integer changpianId = changpianOrder.getChangpianId();

        if (changpianId == null)
            return R.error(511, "查不到该商品");
        ChangpianEntity changpianEntity = changpianService.selectById(changpianId);
        if (changpianEntity == null)
            return R.error(511, "查不到该商品");

        YonghuEntity yonghuEntity = yonghuService.selectById(userId);
        if (yonghuEntity == null)
            return R.error(511, "用户不能为空");
        if (yonghuEntity.getNewMoney() == null)
            return R.error(511, "用户金额不能为空");

        // 余额支付的退款处理
        if (changpianOrderPaymentTypes == 1) {
            // ★ 修复：使用订单实付价格退还余额（而非重新计算，避免价格/折扣变化导致金额不一致）
            Double refundMoney = changpianOrder.getChangpianOrderTruePrice();
            if (refundMoney != null && refundMoney > 0) {
                yonghuEntity.setNewMoney(yonghuEntity.getNewMoney() + refundMoney);
            }

            // 扣减积分
            Double buyJifen = new BigDecimal(changpianEntity.getChangpianPrice())
                    .multiply(new BigDecimal(buyNumber)).doubleValue();
            yonghuEntity.setYonghuSumJifen(yonghuEntity.getYonghuSumJifen() - buyJifen);
            if (yonghuEntity.getYonghuSumJifen() < 0) {
                yonghuEntity.setYonghuSumJifen(0.0);
            }

            // 会员等级重算（从 DictionaryService 可配置阈值）
            yonghuEntity.setHuiyuandengjiTypes(dictionaryService.computeHuiyuandengjiLevel(yonghuEntity.getYonghuSumJifen()));
        }

        // 恢复库存
        changpianEntity.setChangpianKucunNumber(changpianEntity.getChangpianKucunNumber() + buyNumber);

        // ★ 原子写入：订单状态 + 用户 + 商品库存 在同一事务内
        changpianOrder.setChangpianOrderTypes(2); // 退款
        this.updateById(changpianOrder);
        yonghuService.updateById(yonghuEntity);
        changpianService.updateById(changpianEntity);

        return R.ok();
    }

}
