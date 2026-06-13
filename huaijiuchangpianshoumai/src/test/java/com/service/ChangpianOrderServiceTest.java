package com.service;

import com.entity.ChangpianEntity;
import com.entity.ChangpianOrderEntity;
import com.entity.YonghuEntity;
import com.baomidou.mybatisplus.mapper.EntityWrapper;
import com.utils.R;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * 并发下单 & 退款一致性集成测试
 *
 * 前置条件（请在测试数据库中准备好以下数据）:
 * ---------------------------------------------------------------
 * 1) yonghu 表: id=1, new_money=100000, yonghu_sum_jifen=0, huiyuandengji_types=1
 * 2) changpian 表: id=1, changpian_kucun_number=10, changpian_new_money=100,
 *    changpian_price=10, changpian_name='测试唱片'
 * 3) dictionary 表（折扣）:
 *    dic_code='huiyuandengji_types', dic_name='会员等级类型',
 *    code_index=1, index_name='普通会员', beizhu='1.0'
 * 4) dictionary 表（会员等级阈值，可选 -- 不配则使用默认值 10000/100000/1000000）:
 *    dic_code='huiyuandengji_threshold', code_index=1, beizhu='0'
 *    dic_code='huiyuandengji_threshold', code_index=2, beizhu='10000'
 *    dic_code='huiyuandengji_threshold', code_index=3, beizhu='100000'
 * ---------------------------------------------------------------
 *
 * 如果本地没有 Spring 上下文可加载，可将本文件作为 curl 验证的参考脚本使用。
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {
        "classpath:spring/spring.xml",
        "classpath:spring/spring-mvc.xml"
})
public class ChangpianOrderServiceTest {

    @Autowired
    private ChangpianOrderService changpianOrderService;

    @Autowired
    private ChangpianService changpianService;

    @Autowired
    private YonghuService yonghuService;

    private MockHttpServletRequest request;

    @Before
    public void setUp() {
        request = new MockHttpServletRequest();
    }

    // =====================================================================
    // 测试1：并发下单不能超卖
    // 库存=10，20 个线程各买 1 件，最终只能成功 10 单
    // =====================================================================
    @Test
    public void testConcurrentOrderNoOversell() throws Exception {
        final int THREAD_COUNT = 20;
        final int STOCK = 10;
        final Integer userId = 1;
        final Integer addressId = 1;
        final Integer changpianId = 1;

        // 重置库存和用户余额
        ChangpianEntity cp = changpianService.selectById(changpianId);
        cp.setChangpianKucunNumber(STOCK);
        changpianService.updateById(cp);

        YonghuEntity yh = yonghuService.selectById(userId);
        yh.setNewMoney(100000.0);
        yh.setYonghuSumJifen(0.0);
        yh.setHuiyuandengjiTypes(1);
        yonghuService.updateById(yh);

        // 并发下单
        final CountDownLatch ready = new CountDownLatch(THREAD_COUNT);
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(THREAD_COUNT);
        final AtomicInteger successCount = new AtomicInteger(0);
        final AtomicInteger failCount = new AtomicInteger(0);

        ExecutorService pool = Executors.newFixedThreadPool(THREAD_COUNT);
        for (int i = 0; i < THREAD_COUNT; i++) {
            pool.submit(new Runnable() {
                public void run() {
                    try {
                        ready.countDown();
                        start.await(); // 所有线程同时起跑

                        List<Map> items = new ArrayList<Map>();
                        Map<String, Object> item = new HashMap<String, Object>();
                        item.put("changpianId", changpianId);
                        item.put("buyNumber", 1);
                        items.add(item);

                        R result = changpianOrderService.placeOrder(
                                userId, addressId, 1, items, request);

                        if (result.get("code") != null
                                && Integer.valueOf(0).equals(result.get("code"))) {
                            successCount.incrementAndGet();
                        } else {
                            failCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                }
            });
        }

        ready.await();  // 等待所有线程就绪
        start.countDown(); // 发令枪
        done.await();     // 等待全部完成
        pool.shutdown();

        // 验证：不能超卖
        ChangpianEntity cpAfter = changpianService.selectById(changpianId);
        System.out.println("=== 并发下单测试结果 ===");
        System.out.println("成功单数: " + successCount.get());
        System.out.println("失败单数: " + failCount.get());
        System.out.println("剩余库存: " + cpAfter.getChangpianKucunNumber());

        assertTrue("不能超卖：剩余库存不应为负", cpAfter.getChangpianKucunNumber() >= 0);
        assertEquals("成功单数应等于初始库存", STOCK, successCount.get());
        assertEquals("剩余库存应为 0", 0, (int) cpAfter.getChangpianKucunNumber());
    }


    // =====================================================================
    // 测试2：退款一致性（余额 + 积分 + 会员等级 + 库存一起回滚）
    // =====================================================================
    @Test
    public void testRefundConsistency() throws Exception {
        final Integer userId = 1;
        final Integer addressId = 1;
        final Integer changpianId = 1;

        // 重置数据
        ChangpianEntity cp = changpianService.selectById(changpianId);
        cp.setChangpianKucunNumber(100);
        changpianService.updateById(cp);

        YonghuEntity yh = yonghuService.selectById(userId);
        double originalMoney = 100000.0;
        double originalJifen = 5000.0;
        yh.setNewMoney(originalMoney);
        yh.setYonghuSumJifen(originalJifen);
        yh.setHuiyuandengjiTypes(1);
        yonghuService.updateById(yh);

        // 先下一单：买 5 件，单价 100，折扣 1.0，积分 10/件
        List<Map> items = new ArrayList<Map>();
        Map<String, Object> item = new HashMap<String, Object>();
        item.put("changpianId", changpianId);
        item.put("buyNumber", 5);
        items.add(item);

        R orderResult = changpianOrderService.placeOrder(
                userId, addressId, 1, items, request);
        System.out.println("下单结果: " + orderResult);

        // 读取下单后的状态
        YonghuEntity yhAfterOrder = yonghuService.selectById(userId);
        ChangpianEntity cpAfterOrder = changpianService.selectById(changpianId);
        double moneyAfterOrder = yhAfterOrder.getNewMoney();
        double jifenAfterOrder = yhAfterOrder.getYonghuSumJifen();
        int stockAfterOrder = cpAfterOrder.getChangpianKucunNumber();
        System.out.println("下单后 -- 余额: " + moneyAfterOrder
                + ", 积分: " + jifenAfterOrder
                + ", 库存: " + stockAfterOrder);

        // 找到刚创建的订单
        List<ChangpianOrderEntity> orders = changpianOrderService.selectList(
                new EntityWrapper<ChangpianOrderEntity>()
                        .eq("yonghu_id", userId)
                        .eq("changpian_id", changpianId)
                        .eq("changpian_order_types", 3)
                        .orderBy("id", false));
        assertFalse("应该有已支付订单", orders.isEmpty());
        Integer orderId = orders.get(0).getId();

        // 执行退款
        R refundResult = changpianOrderService.refundOrder(orderId, userId, request);
        System.out.println("退款结果: " + refundResult);

        // 验证退款后的状态
        YonghuEntity yhAfterRefund = yonghuService.selectById(userId);
        ChangpianEntity cpAfterRefund = changpianService.selectById(changpianId);
        ChangpianOrderEntity orderAfterRefund = changpianOrderService.selectById(orderId);

        System.out.println("退款后 -- 余额: " + yhAfterRefund.getNewMoney()
                + ", 积分: " + yhAfterRefund.getYonghuSumJifen()
                + ", 库存: " + cpAfterRefund.getChangpianKucunNumber()
                + ", 会员等级: " + yhAfterRefund.getHuiyuandengjiTypes()
                + ", 订单状态: " + orderAfterRefund.getChangpianOrderTypes());

        // 余额应恢复
        assertEquals("退款后余额应恢复到下单前", originalMoney,
                yhAfterRefund.getNewMoney(), 0.01);
        // 积分应恢复
        assertEquals("退款后积分应恢复到下单前", originalJifen,
                yhAfterRefund.getYonghuSumJifen(), 0.01);
        // 库存应恢复
        assertEquals("退款后库存应恢复到下单前", 100,
                (int) cpAfterRefund.getChangpianKucunNumber());
        // 订单状态应为退款(2)
        assertEquals("退款后订单状态应为2(退款)",
                Integer.valueOf(2), orderAfterRefund.getChangpianOrderTypes());
    }


    // =====================================================================
    // 测试3：状态机守卫 -- 非法状态转换应被拒绝
    // =====================================================================
    @Test
    public void testStateMachineGuards() throws Exception {
        final Integer userId = 1;
        final Integer changpianId = 1;

        // 创建一个"已支付(3)"的订单用于测试
        ChangpianOrderEntity testOrder = new ChangpianOrderEntity();
        testOrder.setChangpianOrderUuidNumber("TEST_" + System.currentTimeMillis());
        testOrder.setAddressId(1);
        testOrder.setChangpianId(changpianId);
        testOrder.setYonghuId(userId);
        testOrder.setBuyNumber(1);
        testOrder.setChangpianOrderTypes(3); // 已支付
        testOrder.setChangpianOrderPaymentTypes(1);
        testOrder.setChangpianOrderTruePrice(100.0);
        testOrder.setInsertTime(new Date());
        testOrder.setCreateTime(new Date());
        changpianOrderService.insert(testOrder);
        Integer orderId = testOrder.getId();

        // 1. 待支付状态不能收货
        R receiveResult = changpianOrderService.receiveOrder(orderId);
        System.out.println("待支付->收货: " + receiveResult);
        assertNotEquals("已支付状态不应允许收货",
                Integer.valueOf(0), receiveResult.get("code"));

        // 2. 已支付可以发货
        R deliverResult = changpianOrderService.deliverOrder(orderId, "SF123", "顺丰");
        System.out.println("已支付->发货: " + deliverResult);
        assertEquals("已支付状态应允许发货",
                Integer.valueOf(0), deliverResult.get("code"));

        // 3. 已发货不能再发货
        R deliverAgain = changpianOrderService.deliverOrder(orderId, "SF456", "顺丰");
        System.out.println("已发货->再发货: " + deliverAgain);
        assertNotEquals("已发货状态不应允许再次发货",
                Integer.valueOf(0), deliverAgain.get("code"));

        // 4. 已发货可以收货
        R receiveOk = changpianOrderService.receiveOrder(orderId);
        System.out.println("已发货->收货: " + receiveOk);
        assertEquals("已发货状态应允许收货",
                Integer.valueOf(0), receiveOk.get("code"));

        // 5. 已收货不能再收货
        R receiveAgain = changpianOrderService.receiveOrder(orderId);
        System.out.println("已收货->再收货: " + receiveAgain);
        assertNotEquals("已收货状态不应允许再次收货",
                Integer.valueOf(0), receiveAgain.get("code"));

        // 6. 已收货(5)不能退款
        R refundAfterReceive = changpianOrderService.refundOrder(orderId, userId, request);
        System.out.println("已收货->退款: " + refundAfterReceive);
        assertNotEquals("已收货状态不应允许退款",
                Integer.valueOf(0), refundAfterReceive.get("code"));
    }
}
