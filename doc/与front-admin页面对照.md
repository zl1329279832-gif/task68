# 与 front/admin 页面对照

> 本文档说明前端用户页面（front）和后台管理页面（admin）中各按钮/操作对应的后端接口，帮助客服快速定位用户操作与系统行为的关系。

---

## 目录

1. [页面总览](#1-页面总览)
2. [用户前台（front）— 购物车页面](#2-用户前台front-购物车页面)
3. [用户前台（front）— 确认订单页面](#3-用户前台front-确认订单页面)
4. [用户前台（front）— 订单列表页面](#4-用户前台front-订单列表页面)
5. [后台管理（admin）— 订单管理页面](#5-后台管理admin-订单管理页面)
6. [后台管理（admin）— 字典管理页面](#6-后台管理admin-字典管理页面)
7. [按钮可见性条件速查表](#7-按钮可见性条件速查表)
8. [接口依赖关系图](#8-接口依赖关系图)

---

## 1. 页面总览

### 用户前台（front）

| 页面 | 文件路径 | 入口 |
|---|---|---|
| 购物车 | `front/pages/cart/list.html` | 导航栏购物车图标 |
| 确认订单 | `front/pages/changpianOrder/confirm.html` | 购物车页面点击「下单」跳转 |
| 我的订单 | `front/pages/changpianOrder/list.html` | 个人中心「商品订单」菜单 |

### 后台管理（admin）

| 页面 | 文件路径 | 路由 |
|---|---|---|
| 订单管理 | `admin/src/views/modules/changpianOrder/list.vue` | `/changpianOrder` |
| 订单类型字典 | `admin/src/views/modules/dictionaryChangpianOrder/list.vue` | `/dictionaryChangpianOrder` |
| 支付类型字典 | `admin/src/views/modules/dictionaryChangpianOrderPayment/list.vue` | `/dictionaryChangpianOrderPayment` |
| 会员等级字典 | （通用字典页面） | `/dictionaryHuiyuandengji` |

---

## 2. 用户前台（front）— 购物车页面

文件：`front/pages/cart/list.html`

### 页面加载时调用的接口

| 时机 | 接口 | 方法 | 说明 |
|---|---|---|---|
| 页面初始化 | `GET /cart/page?yonghuId=xxx` | GET | 获取当前用户购物车列表 |
| 页面初始化 | `GET /config/list` | GET | 获取轮播图配置 |

### 按钮与操作

| 按钮/操作 | 触发位置 | 调用接口 | 方法 | 参数 | 说明 |
|---|---|---|---|---|---|
| **"+" 增加数量** | 每个商品行右侧 | `GET /changpian/info/{changpianId}` | GET | 商品 ID | 先查商品详情校验库存 |
| | | `POST /cart/update` | POST | 完整 cart 对象（buyNumber+1） | 库存充足时更新购物车数量 |
| **"-" 减少数量** | 每个商品行右侧 | `POST /cart/update` | POST | 完整 cart 对象（buyNumber-1） | 数量最小为 1 |
| **"移除商品"** | 每个商品行右侧 | `POST /cart/delete` | POST | `[cartId]`（数组） | 删除购物车条目，页面刷新 |
| **"下单"** | 页面底部悬浮栏 | 无 API 调用 | — | — | 将 dataList 写入 `localStorage('changpians')`，跳转 confirm.html |

> **数据传递方式**：购物车页面到确认订单页面通过 `localStorage` 传递商品数据，而非通过后端 API。

---

## 3. 用户前台（front）— 确认订单页面

文件：`front/pages/changpianOrder/confirm.html`

### 页面加载时调用的接口

| 时机 | 接口 | 方法 | 说明 |
|---|---|---|---|
| 页面初始化 | `GET /address/page?yonghuId=xxx` | GET | 获取用户收货地址列表 |
| 页面初始化 | `GET /yonghu/session` | GET | 获取当前用户信息（含会员等级） |
| 获取用户信息后 | `GET /dictionary/page?dicCode=huiyuandengji_types&dicName=会员等级类型&codeIndexStart=等级&codeIndexEnd=等级` | GET | 获取当前等级对应的折扣系数（beizhu 字段） |
| 页面初始化 | 读取 `localStorage('changpians')` | — | 获取购物车传来的商品列表 |

### 页面显示逻辑

- 商品列表：从 localStorage 读取，展示名称、现价、数量、行总价
- 总价计算（前端）：`SUM(changpianNewMoney × buyNumber)`
- 实付金额计算（前端）：`总价 × zhekou`（折扣系数）
- 地址选择：radio 按钮，默认选中 `isdefaultTypes == 2` 的地址

### 按钮与操作

| 按钮/操作 | 触发位置 | 调用接口 | 方法 | 参数 | 说明 |
|---|---|---|---|---|---|
| **"提交订单"** | 页面底部 | `POST /changpianOrder/order` | POST | `addressId`, `changpians`（JSON 字符串）, `yonghuId`, `changpianOrderPaymentTypes` | 完整下单流程 |

> **提交订单后**：清除 `localStorage('changpians')`，弹出"下单成功"提示，跳转到订单列表页。

---

## 4. 用户前台（front）— 订单列表页面

文件：`front/pages/changpianOrder/list.html`

### 页面加载时调用的接口

| 时机 | 接口 | 方法 | 说明 |
|---|---|---|---|
| 页面初始化 | `GET /changpianOrder/page?page=1&limit=8` | GET | 获取当前用户的订单列表（后端自动按 session userId 过滤） |
| 页面初始化 | `GET /dictionary/page?dicCode=changpian_order_types` | GET | 获取订单类型字典（用于 Tab 标签页） |
| 页面初始化 | `GET /dictionary/page?dicCode=changpian_order_payment_types` | GET | 获取支付类型字典 |

### Tab 标签切换

| Tab 名称 | 触发操作 | 调用接口 | 说明 |
|---|---|---|---|
| 全部商品订单 | 点击 Tab | `GET /changpianOrder/page?page=1&limit=8` | 不传 changpianOrderTypes |
| 已评价 / 退款 / 已支付 / 已发货 / 已收货 | 点击对应 Tab | `GET /changpianOrder/page?changpianOrderTypes=N` | 按状态值过滤 |

### 按钮与操作

| 按钮 | 显示条件 | 调用接口 | 方法 | 确认提示 | 说明 |
|---|---|---|---|---|---|
| **"退款"** | `changpianOrderTypes == 3`（已支付） | `GET /changpianOrder/refund?id=订单ID` | GET | confirm("确定要退款吗？") | 退款后页面刷新 |
| **"收货"** | `changpianOrderTypes == 4`（已发货） | `GET /changpianOrder/receiving?id=订单ID` | GET | confirm("确定要收货吗？") | 确认收货后页面刷新 |
| **"评价"** | `changpianOrderTypes == 5`（已收货） | `GET /changpianOrder/commentback?id=订单ID&commentbackText=内容&changpianCommentbackPingfenNumber=评分` | GET | 弹出评论输入模态框 | 提交评价内容和评分 |

---

## 5. 后台管理（admin）— 订单管理页面

文件：`admin/src/views/modules/changpianOrder/list.vue`

### 页面加载时调用的接口

| 时机 | 接口 | 方法 | 说明 |
|---|---|---|---|
| 页面初始化 | `GET /changpianOrder/page?page=1&limit=10&sort=id` | GET | 获取订单分页列表 |
| 页面初始化 | `GET /dictionary/page?dicCode=changpian_types` | GET | 获取商品类型字典（用于搜索下拉框） |

### 搜索条件

| 搜索项 | 参数名 | 说明 |
|---|---|---|
| 商品名称 | `changpianName` | 模糊匹配（加 `%` 前后缀） |
| 商品类型 | `changpianTypes` | 下拉选择，精确匹配 |
| 用户姓名 | `yonghuName` | 模糊匹配 |

### 工具栏按钮

| 按钮 | 权限标识 | 调用接口 | 方法 | 说明 |
|---|---|---|---|---|
| **"新增"** | `changpianOrder:新增` | 打开新增/编辑组件 | — | 跳转到表单页面 |
| **"删除"（批量）** | `changpianOrder:删除` | `POST /changpianOrder/delete` | POST | 批量删除选中订单 |
| **"报表"** | `changpianOrder:报表` | `GET /barSum` | GET | 打开 ECharts 统计图弹窗 |
| **"批量导入模板"** | `changpianOrder:导入导出` | 下载 xls 模板文件 | — | 直接下载链接 |
| **"批量导入"** | `changpianOrder:导入导出` | `POST /file/upload` → `GET /changpianOrder/batchInsert?fileName=xxx` | POST+GET | 先上传文件再调用导入 |
| **"导出"** | `changpianOrder:导入导出` | 无 API 调用（前端直接导出 dataList） | — | 使用 vue-json-excel 插件客户端导出 |

### 每行操作按钮

| 按钮 | 权限标识 | 额外显示条件 | 调用接口 | 方法 | 说明 |
|---|---|---|---|---|---|
| **"详情"** | `changpianOrder:查看` | 无 | 打开详情组件 | — | 查看订单完整信息 |
| **"修改"** | `changpianOrder:修改` | 无 | 打开编辑组件 | — | 编辑订单字段 |
| **"删除"** | `changpianOrder:删除` | 无 | `POST /changpianOrder/delete` | POST | 删除单条订单 |
| **"退款"** | `changpianOrder:订单` | `status==3` 且 `sessionTable=='yonghu'` 且 `userId==order.yonghuId` | `POST /changpianOrder/refund?id=订单ID` | POST | 仅订单所属用户可退款 |
| **"发货"** | `changpianOrder:订单` | `status==3` 且 `sessionTable=='users'`（管理员） | `POST /changpianOrder/deliver?id=xxx&changpianOrderCourierNumber=单号&changpianOrderCourierName=公司` | POST | 弹出模态框填写快递信息 |
| **"收货"** | `changpianOrder:订单` | `status==4` 且 `sessionTable=='yonghu'` 且 `userId==order.yonghuId` | `POST /changpianOrder/receiving?id=订单ID` | POST | 仅订单所属用户可收货 |
| **"评价"** | `changpianOrder:订单` | `status==5` 且 `sessionTable=='yonghu'` 且 `userId==order.yonghuId` | `POST /changpianOrder/commentback?id=xxx&commentbackText=内容&changpianCommentbackPingfenNumber=评分` | POST | 弹出模态框填写评价 |

### 发货模态框

| 字段 | 参数名 | 校验 | 说明 |
|---|---|---|---|
| 快递公司 | `changpianOrderCourierName` | 前端非空校验 | 文本输入 |
| 快递单号 | `changpianOrderCourierNumber` | 前端非空校验 | 文本输入 |

---

## 6. 后台管理（admin）— 字典管理页面

| 页面 | 路由 | 字典 dicCode | 作用 |
|---|---|---|---|
| 订单类型字典 | `/dictionaryChangpianOrder` | `changpian_order_types` | 管理订单状态的中文名称映射（如 1=已评价、2=退款 等） |
| 支付类型字典 | `/dictionaryChangpianOrderPayment` | `changpian_order_payment_types` | 管理支付方式的中文名称映射（如 1=余额支付） |
| 会员等级字典 | `/dictionaryHuiyuandengji` | `huiyuandengji_types` | 管理会员等级名称和折扣系数（beizhu 字段存折扣） |

> **重要**：修改字典表中会员等级的 `beizhu`（折扣系数）会直接影响所有新订单的定价和所有退款的退款金额。

---

## 7. 按钮可见性条件速查表

以下表格汇总所有订单操作按钮在 front 和 admin 中的显示条件：

| 操作 | 页面 | 订单状态 | 角色要求 | 归属校验（前端） | 后端校验 |
|---|---|---|---|---|---|
| 退款 | front 订单列表 | `== 3` | 用户 | 无（后端自动按 session 过滤列表） | 状态校验，无身份校验 |
| 退款 | admin 订单管理 | `== 3` | `sessionTable=='yonghu'` | `userId==order.yonghuId` | 状态校验，无身份校验 |
| 发货 | admin 订单管理 | `== 3` | `sessionTable=='users'`（管理员） | 无 | 状态校验，无角色校验 |
| 收货 | front 订单列表 | `== 4` | 用户 | 无 | 状态校验，无身份校验 |
| 收货 | admin 订单管理 | `== 4` | `sessionTable=='yonghu'` | `userId==order.yonghuId` | 状态校验，无身份校验 |
| 评价 | front 订单列表 | `== 5` | 用户 | 无 | 状态校验，无身份校验 |
| 评价 | admin 订单管理 | `== 5` | `sessionTable=='yonghu'` | `userId==order.yonghuId` | 状态校验，无身份校验 |

> **安全提示**：所有操作的权限校验仅在前端通过 `v-if` 控制按钮可见性。后端只做状态校验，不做角色和归属校验。这意味着通过直接调用 API（如 Postman）可以绕过前端权限限制。

---

## 8. 接口依赖关系图

下图展示用户从浏览商品到完成订单全流程中，各页面依赖的接口链路：

```
用户前台页面                        后端接口                         影响
──────────                        ────────                       ─────

购物车页面 (cart/list.html)
  ├─ 页面加载 ──────────────> GET /cart/page              → 读取购物车
  ├─ [+] 按钮 ──────────────> GET /changpian/info/{id}    → 校验库存
  │                     └──> POST /cart/update            → 更新数量
  ├─ [-] 按钮 ──────────────> POST /cart/update            → 更新数量
  ├─ [移除] 按钮 ───────────> POST /cart/delete            → 删除条目
  └─ [下单] 按钮 ───────────> localStorage 写入 ──┐
                                                  │
确认订单页面 (confirm.html)                        │
  ├─ 页面加载 ←── localStorage 读取 ──────────────┘
  ├─ 页面加载 ──────────────> GET /address/page            → 读取地址
  ├─ 页面加载 ──────────────> GET /yonghu/session           → 读取用户信息
  ├─ 页面加载 ──────────────> GET /dictionary/page          → 读取折扣系数
  └─ [提交订单] ────────────> POST /changpianOrder/order    → 扣余额/库存，加积分
                                                              ├→ INSERT 订单
                                                              ├→ UPDATE 商品库存
                                                              ├→ UPDATE 用户余额/积分/等级
                                                              └→ DELETE 购物车

订单列表页面 (changpianOrder/list.html)
  ├─ 页面加载 ──────────────> GET /changpianOrder/page      → 读取订单
  ├─ [退款] 按钮 ───────────> GET /changpianOrder/refund    → 退余额/库存，扣积分
  ├─ [收货] 按钮 ───────────> GET /changpianOrder/receiving  → 更新状态→5
  └─ [评价] 按钮 ───────────> GET /changpianOrder/commentback → 更新状态→1

后台管理页面 (admin changpianOrder/list.vue)
  ├─ 页面加载 ──────────────> GET /changpianOrder/page      → 读取订单
  ├─ [发货] 按钮 ───────────> POST /changpianOrder/deliver   → 更新状态→4，写入快递信息
  ├─ [退款] 按钮 ───────────> POST /changpianOrder/refund    → 退余额/库存，扣积分
  ├─ [收货] 按钮 ───────────> POST /changpianOrder/receiving  → 更新状态→5
  ├─ [评价] 按钮 ───────────> POST /changpianOrder/commentback → 更新状态→1
  ├─ [删除] 按钮 ───────────> POST /changpianOrder/delete    → 物理删除订单记录
  └─ [批量导入] ────────────> POST /file/upload
                         └──> GET /changpianOrder/batchInsert → 批量插入订单
```

---

## 附录：front 与 admin 调用同一接口的 HTTP 方法差异

注意：部分接口在 front 和 admin 中使用了不同的 HTTP 方法，但后端 Controller 使用 `@RequestMapping`（不限定 HTTP 方法），所以 GET/POST 均可访问：

| 接口 | front 使用的方法 | admin 使用的方法 | 后端注解 |
|---|---|---|---|
| `/changpianOrder/refund` | GET | POST | `@RequestMapping`（GET/POST 均可） |
| `/changpianOrder/receiving` | GET | POST | `@RequestMapping`（GET/POST 均可） |
| `/changpianOrder/commentback` | GET | POST | `@RequestMapping`（GET/POST 均可） |
| `/changpianOrder/deliver` | — | POST | `@RequestMapping`（GET/POST 均可） |
