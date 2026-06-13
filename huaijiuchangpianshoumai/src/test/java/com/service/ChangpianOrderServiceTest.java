package com.service;

import com.entity.ChangpianEntity;
import com.entity.YonghuEntity;
import com.utils.R;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 商品订单服务测试
 * 覆盖并发下单防超卖、退款一致性回滚
 *
 * 运行前提：
 *   1. MySQL 中存在 huaijiuchangpianshoumai 库
 *   2. 存在测试用商品（changpian 表），库存 >= 1
 *   3. 存在测试用户（yonghu 表），余额充足
 *   4. dictionary 表已初始化会员等级数据
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {
    "classpath:spring/spring.xml",
    "classpath:spring/spring-mybatis.xml"
})
public class ChangpianOrderServiceTest {

    @Autowired
    private ChangpianOrderService changpianOrderService;
    @Autowired
    private ChangpianService changpianService;
    @Autowired
    private YonghuService yonghuService;

    /**
     * 测试：并发下单不超卖
     *
     * 场景：商品库存为 5，10 个线程各下 1 件
     *       期望：最多成功 5 单，库存归零不为负
     */
    @Test
    public void testConcurrentOrderNoOversell() throws Exception {
        // ---- 准备：找一个有库存的商品和一个有余额的用户 ----
        // 请根据实际数据库替换 ID
        final int TEST_CHANGPIAN_ID = 1;
        final int TEST_USER_ID = 1;
        final int TEST_ADDRESS_ID = 1;
        final int STOCK = 5;
        final int THREAD_COUNT = 10;

        // 重置库存为 STOCK
        ChangpianEntity cp = changpianService.selectById(TEST_CHANGPIAN_ID);
        Assert.assertNotNull("测试商品不存在，请调整 TEST_CHANGPIAN_ID", cp);
        cp.setChangpianKucunNumber(STOCK);
        changpianService.updateById(cp);

        // 确保用户余额充足
        YonghuEntity user = yonghuService.selectById(TEST_USER_ID);
        Assert.assertNotNull("测试用户不存在，请调整 TEST_USER_ID", user);
        user.setNewMoney(999999.0);
        user.setYonghuSumJifen(0.0);
        user.setHuiyuandengjiTypes(1);
        yonghuService.updateById(user);

        // ---- 并发下单 ----
        ExecutorService pool = Executors.newFixedThreadPool(THREAD_COUNT);
        final CountDownLatch latch = new CountDownLatch(1);  // 让所有线程同时起跑
        final AtomicInteger successCount = new AtomicInteger(0);
        final AtomicInteger failCount = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < THREAD_COUNT; i++) {
            futures.add(pool.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        latch.await(); // 等待发令枪
                        Map<String, Object> item = new HashMap<>();
                        item.put("changpianId", TEST_CHANGPIAN_ID);
                        item.put("buyNumber", 1);
                        item.put("id", "");
                        List<Map<String, Object>> items = new ArrayList<>();
                        items.add(item);

                        R result = changpianOrderService.placeOrder(
                            TEST_USER_ID, TEST_ADDRESS_ID, 1, (List) items);
                        if ((int) result.get("code") == 0) {
                            successCount.incrementAndGet();
                        } else {
                            failCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    }
                }
            }));
        }

        latch.countDown(); // 发令！
        for (Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        // ---- 断言 ----
        ChangpianEntity after = changpianService.selectById(TEST_CHANGPIAN_ID);
        System.out.println("成功下单数: " + successCount.get());
        System.out.println("失败下单数: " + failCount.get());
        System.out.println("剩余库存: " + after.getChangpianKucunNumber());

        Assert.assertTrue("成功数不应超过库存", successCount.get() <= STOCK);
        Assert.assertTrue("库存不能为负", after.getChangpianKucunNumber() >= 0);
        Assert.assertEquals("成功数 + 剩余库存 = 初始库存",
            STOCK, successCount.get() + after.getChangpianKucunNumber());
    }

    /**
     * 测试：退款一致性回滚
     *
     * 场景：下一单 -> 退款 -> 验证余额回来、积分扣回、库存恢复、状态变 2
     */
    @Test
    public void testRefundConsistency() {
        final int TEST_CHANGPIAN_ID = 1;
        final int TEST_USER_ID = 1;
        final int TEST_ADDRESS_ID = 1;

        // ---- 准备初始状态 ----
        ChangpianEntity cp = changpianService.selectById(TEST_CHANGPIAN_ID);
        Assert.assertNotNull("测试商品不存在", cp);
        cp.setChangpianKucunNumber(100);
        changpianService.updateById(cp);

        YonghuEntity user = yonghuService.selectById(TEST_USER_ID);
        Assert.assertNotNull("测试用户不存在", user);
        double initBalance = 50000.0;
        double initJifen = 0.0;
        user.setNewMoney(initBalance);
        user.setYonghuSumJifen(initJifen);
        user.setHuiyuandengjiTypes(1);
        yonghuService.updateById(user);

        int initStock = 100;

        // ---- 下单 ----
        Map<String, Object> item = new HashMap<>();
        item.put("changpianId", TEST_CHANGPIAN_ID);
        item.put("buyNumber", 2);
        item.put("id", "");
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(item);

        R orderResult = changpianOrderService.placeOrder(
            TEST_USER_ID, TEST_ADDRESS_ID, 1, (List) items);
        Assert.assertEquals("下单应成功", 0, orderResult.get("code"));

        // 下单后：余额减少、积分增加、库存减少
        YonghuEntity afterOrder = yonghuService.selectById(TEST_USER_ID);
        ChangpianEntity cpAfterOrder = changpianService.selectById(TEST_CHANGPIAN_ID);
        Assert.assertTrue("余额应减少", afterOrder.getNewMoney() < initBalance);
        Assert.assertTrue("积分应增加", afterOrder.getYonghuSumJifen() > initJifen);
        Assert.assertEquals("库存应减 2", initStock - 2, (int) cpAfterOrder.getChangpianKucunNumber());

        double balanceAfterOrder = afterOrder.getNewMoney();
        double jifenAfterOrder = afterOrder.getYonghuSumJifen();

        // ---- 找到刚创建的订单并退款 ----
        // 取最新的订单
        List<com.entity.ChangpianOrderEntity> orders = changpianOrderService.selectList(
            new com.baomidou.mybatisplus.mapper.EntityWrapper<com.entity.ChangpianOrderEntity>()
                .eq("yonghu_id", TEST_USER_ID)
                .eq("changpian_id", TEST_CHANGPIAN_ID)
                .eq("changpian_order_types", 3)
                .orderBy("id", false)
        );
        Assert.assertFalse("应该找到已支付订单", orders.isEmpty());
        Integer orderId = orders.get(0).getId();

        R refundResult = changpianOrderService.refundOrder(orderId, TEST_USER_ID);
        Assert.assertEquals("退款应成功", 0, refundResult.get("code"));

        // ---- 退款后断言 ----
        YonghuEntity afterRefund = yonghuService.selectById(TEST_USER_ID);
        ChangpianEntity cpAfterRefund = changpianService.selectById(TEST_CHANGPIAN_ID);
        com.entity.ChangpianOrderEntity refundedOrder = changpianOrderService.selectById(orderId);

        System.out.println("退款后余额: " + afterRefund.getNewMoney() + " (初始: " + initBalance + ")");
        System.out.println("退款后积分: " + afterRefund.getYonghuSumJifen() + " (初始: " + initJifen + ")");
        System.out.println("退款后库存: " + cpAfterRefund.getChangpianKucunNumber() + " (初始: " + initStock + ")");
        System.out.println("订单状态: " + refundedOrder.getChangpianOrderTypes());

        // 余额应恢复
        Assert.assertTrue("退款后余额应回来", afterRefund.getNewMoney() > balanceAfterOrder);
        // 积分应回退
        Assert.assertTrue("退款后积分应减少", afterRefund.getYonghuSumJifen() < jifenAfterOrder);
        // 库存应恢复
        Assert.assertEquals("退款后库存应恢复", initStock, (int) cpAfterRefund.getChangpianKucunNumber());
        // 订单状态应为 2（退款）
        Assert.assertEquals("订单状态应为退款(2)", Integer.valueOf(2), refundedOrder.getChangpianOrderTypes());
    }
}
