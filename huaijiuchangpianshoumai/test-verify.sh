#!/bin/bash
##############################################################################
# 电商主链路 curl 验证脚本
# 用法: bash test-verify.sh [BASE_URL]
# 默认: http://localhost:8080/huaijiuchangpianshoumai
#
# 前提:
#   1. 应用已启动
#   2. 数据库中存在测试用户 (yonghu 表, 余额充足)
#   3. 数据库中存在测试商品 (changpian 表, 库存充足)
#   4. dictionary 表有会员等级数据
#
# 请先通过登录接口获取 Token，填入下面的变量
##############################################################################

BASE=${1:-"http://localhost:8080/huaijiuchangpianshoumai"}
TOKEN="替换为实际Token"   # 先调用 /yonghu/login 获取

echo "========================================"
echo "  1. 并发下单防超卖测试"
echo "========================================"
echo ""
echo "-- 步骤 1.1: 先把商品 id=1 库存设为 3 (通过后台接口或直接 SQL)"
echo "   UPDATE changpian SET changpian_kucun_number = 3 WHERE id = 1;"
echo ""
echo "-- 步骤 1.2: 5 个并发请求各买 1 件 (预期: 最多 3 个成功)"

for i in $(seq 1 5); do
  curl -s -X POST "${BASE}/changpianOrder/order?addressId=1&changpianOrderPaymentTypes=1&changpians=[{\"changpianId\":1,\"buyNumber\":1,\"id\":\"\"}]" \
    -H "Token: ${TOKEN}" \
    -H "Content-Type: application/x-www-form-urlencoded" &
done
wait
echo ""

echo "-- 步骤 1.3: 检查库存 (预期: >= 0, 不能为负)"
echo "   SELECT changpian_kucun_number FROM changpian WHERE id = 1;"
echo "   若为 0 且只有 3 单成功 => 防超卖生效"
echo ""

echo "========================================"
echo "  2. 退款一致性验证"
echo "========================================"
echo ""
echo "-- 步骤 2.1: 记录下单前用户余额和积分"
echo "   SELECT new_money, yonghu_sum_jifen, huiyuandengji_types FROM yonghu WHERE id = 1;"
echo ""
echo "-- 步骤 2.2: 正常下单 1 件"
curl -s -X POST "${BASE}/changpianOrder/order?addressId=1&changpianOrderPaymentTypes=1&changpians=[{\"changpianId\":1,\"buyNumber\":1,\"id\":\"\"}]" \
  -H "Token: ${TOKEN}"
echo ""

echo "-- 步骤 2.3: 确认余额减少、积分增加、库存减少"
echo "   SELECT new_money, yonghu_sum_jifen, huiyuandengji_types FROM yonghu WHERE id = 1;"
echo "   SELECT changpian_kucun_number FROM changpian WHERE id = 1;"
echo ""

echo "-- 步骤 2.4: 退款 (替换 ORDER_ID 为实际订单 id)"
echo "   SELECT id FROM changpian_order WHERE yonghu_id=1 AND changpian_order_types=3 ORDER BY id DESC LIMIT 1;"
ORDER_ID=999  # 替换为实际值
curl -s -X POST "${BASE}/changpianOrder/refund?id=${ORDER_ID}" \
  -H "Token: ${TOKEN}"
echo ""

echo "-- 步骤 2.5: 验证退款后一致性"
echo "   SELECT new_money, yonghu_sum_jifen, huiyuandengji_types FROM yonghu WHERE id = 1;"
echo "   SELECT changpian_kucun_number FROM changpian WHERE id = 1;"
echo "   SELECT changpian_order_types FROM changpian_order WHERE id = ${ORDER_ID};"
echo "   预期: 余额回来, 积分扣回, 库存恢复, 订单状态=2(退款)"
echo ""

echo "========================================"
echo "  3. 状态机校验"
echo "========================================"
echo ""
echo "-- 3.1: 待支付状态不能发货 (已支付=3 才能发货)"
echo "   对一个状态为 2(退款) 的订单尝试发货，预期返回错误:"
curl -s -X POST "${BASE}/changpianOrder/deliver?id=${ORDER_ID}&changpianOrderCourierNumber=SF123&changpianOrderCourierName=顺丰" \
  -H "Token: ${TOKEN}"
echo ""
echo "   预期: {\"code\":511,\"msg\":\"只有待发货状态的订单才能发货\"}"
echo ""

echo "-- 3.2: 未发货不能收货 (已发货=4 才能收货)"
echo "   对一个状态为 3(已支付) 的订单尝试收货，预期返回错误:"
curl -s -X POST "${BASE}/changpianOrder/receiving?id=${ORDER_ID}" \
  -H "Token: ${TOKEN}"
echo ""
echo "   预期: {\"code\":511,\"msg\":\"只有已发货状态的订单才能收货\"}"
echo ""

echo "-- 3.3: 已退款的订单不能再退款"
curl -s -X POST "${BASE}/changpianOrder/refund?id=${ORDER_ID}" \
  -H "Token: ${TOKEN}"
echo ""
echo "   预期: {\"code\":511,\"msg\":\"当前订单状态不允许退款\"}"
echo ""
echo "======== 验证完成 ========"
