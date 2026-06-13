# 前端页面与后端 API 对照表

> 本文档说明 admin 后台 Vue 页面和 front 前台 HTML 页面上的每个操作按钮分别调用了哪个后端接口，便于客服和开发人员快速定位问题。
> 最后更新：2026-06-13

---

## 一、admin 后台页面

### 1.1 订单列表页

**文件路径**：`admin/src/views/modules/changpianOrder/list.vue`

**页面加载时调用的接口：**

| 接口 | 用途 |
|---|---|
| `GET /changpianOrder/page` | 获取订单分页列表 |
| `GET /dictionary/page?dicCode=changpian_types` | 获取商品类型下拉选项（搜索条件） |

**工具栏按钮：**

| 按钮 | 依赖接口 | 权限控制 |
|---|---|---|
| 新增 | `GET` -> 跳转 `add-or-update.vue`，`POST /changpianOrder/save` 提交 | `isAuth('changpianOrder','新增')` |
| 删除 | `POST /changpianOrder/delete`（批量） | `isAuth('changpianOrder','删除')` |
| 报表 | `GET /barSum`（ECharts 柱状图） | `isAuth('changpianOrder','报表')` |
| 批量导入模板下载 | 静态文件 `upload/changpianOrderMuBan.xls` | `isAuth('changpianOrder','导入导出')` |
| 批量导入 | `POST /file/upload` -> `GET /changpianOrder/batchInsert` | `isAuth('changpianOrder','导入导出')` |
| 导出 | 前端 `download-excel` 组件（纯前端导出） | `isAuth('changpianOrder','导入导出')` |

**表格行操作按钮：**

| 按钮 | 显示条件（前端 v-if） | 依赖接口 | 说明 |
|---|---|---|---|
| 详情 | `isAuth('changpianOrder','查看')` | `GET /changpianOrder/info/{id}` | 弹窗展示，调用 `add-or-update.vue` |
| 修改 | `isAuth('changpianOrder','修改')` | `GET /changpianOrder/info/{id}` -> `POST /changpianOrder/update` | 弹窗编辑 |
| 删除 | `isAuth('changpianOrder','删除')` | `POST /changpianOrder/delete` | 单条删除 |
| **退款** | `changpianOrderTypes==3 && sessionTable=='yonghu' && userId==row.yonghuId` | `POST /changpianOrder/refund?id=X` | 仅用户本人、状态为"已支付"时显示 |
| **发货** | `changpianOrderTypes==3 && sessionTable=='users'` | `POST /changpianOrder/deliver?id=X&courierNumber=Y&courierName=Z` | 仅管理员、状态为"已支付"时显示；弹窗填写快递信息 |
| **收货** | `changpianOrderTypes==4 && sessionTable=='yonghu' && userId==row.yonghuId` | `POST /changpianOrder/receiving?id=X` | 仅用户本人、状态为"已发货"时显示 |
| **评价** | `changpianOrderTypes==5 && sessionTable=='yonghu' && userId==row.yonghuId` | `POST /changpianOrder/commentback?id=X&commentbackText=Y&changpianCommentbackPingfenNumber=Z` | 仅用户本人、状态为"已收货"时显示；弹窗填写评价内容 |

> **前端按钮可见性 != 后端权限校验**：admin 页面通过 `sessionTable` 和 `userId` 控制按钮显示，但后端 API **未做**对应的身份校验。详见《架构与API对照》第五节风险分析。

### 1.2 订单新增/编辑页

**文件路径**：`admin/src/views/modules/changpianOrder/add-or-update.vue`

| 操作 | 依赖接口 |
|---|---|
| 页面加载（编辑模式） | `GET /changpianOrder/info/{id}` |
| 地址下拉 | `GET /address/page` |
| 商品下拉 | `GET /changpian/page` |
| 用户下拉 | `GET /yonghu/page` |
| 订单类型下拉 | `GET /dictionary/page?dicCode=changpian_order_types` |
| 支付类型下拉 | `GET /dictionary/page?dicCode=changpian_order_payment_types` |
| 提交（新增） | `POST /changpianOrder/save` |
| 提交（编辑） | `POST /changpianOrder/update` |

---

## 二、front 前台页面

### 2.1 确认订单页（购物车结算）

**文件路径**：`front/pages/changpianOrder/confirm.html`

**页面流程与接口对照：**

```
用户从商品页/购物车 -> localStorage 存入 changpians JSON
                     |
               进入 confirm.html
                     |
```

| 步骤 | 依赖接口 | 说明 |
|---|---|---|
| 页面加载 | `GET /yonghu/session` | 获取当前用户信息（会员等级） |
| 获取折扣 | `GET /dictionary/page?dicCode=huiyuandengji_types&codeIndexStart=等级&codeIndexEnd=等级` | 读取当前等级对应的折扣系数（`beizhu`） |
| 获取地址 | `GET /address/page?yonghuId=当前用户ID` | 渲染收货地址列表 |
| 读取商品 | `localStorage.getItem('changpians')` | 从本地存储读取待购买商品（非接口调用） |
| **提交订单** | `POST /changpianOrder/order` | 参数：`addressId`、`changpians`（JSON）、`changpianOrderPaymentTypes`、`yonghuId` |
| 成功后跳转 | -- | 跳转到 `changpianOrder/list.html` |

**页面展示逻辑：**

| 条件 | 展示 |
|---|---|
| `paymentTypes == 1` | 显示"总价"和"实付总额"（含折扣计算） |
| `paymentTypes == 2` | 显示"总价（积分）" |

### 2.2 我的订单列表页

**文件路径**：`front/pages/changpianOrder/list.html`

**页面加载时调用的接口：**

| 接口 | 用途 |
|---|---|
| `GET /dictionary/page?dicCode=changpian_order_types` | 获取订单类型 Tab 标签（全部/已评价/退款/已支付/已发货/已收货） |
| `GET /dictionary/page?dicCode=changpian_order_payment_types` | 获取支付类型下拉选项 |
| `GET /changpianOrder/page` | 获取当前用户的订单列表（后端自动按 userId 过滤） |

**Tab 切换与筛选：**

| Tab | changpianOrderTypes | 说明 |
|---|---|---|
| 全部商品订单 | 不传（null） | 显示所有订单 |
| 已评价 | 1 | |
| 退款 | 2 | |
| 已支付（待发货） | 3 | |
| 已发货 | 4 | |
| 已收货 | 5 | |

**操作按钮与接口对照：**

| 按钮 | 显示条件 | 依赖接口 | 调用方式 |
|---|---|---|---|
| **退款** | `changpianOrderTypes == 3` | `GET /changpianOrder/refund?id=X` | `layui.http.request`，GET 方法 |
| **收货** | `changpianOrderTypes == 4` | `GET /changpianOrder/receiving?id=X` | `layui.http.request`，GET 方法 |
| **评价** | `changpianOrderTypes == 5` | `GET /changpianOrder/commentback?id=X&commentbackText=Y&changpianCommentbackPingfenNumber=Z` | `layui.http.request`，GET 方法 |

> **注意**：前台页面的退款/收货/评价使用的是 **GET 方法**调用，而后端 Controller 上标注的是 `@RequestMapping`（同时接受 GET 和 POST）。admin 后台使用的是 POST 方法。

### 2.3 单商品下单页（旧版）

**文件路径**：`front/pages/changpianOrder/add.html`

| 步骤 | 依赖接口 | 说明 |
|---|---|---|
| 页面加载 | `GET /changpian/detail/{id}` | 从 `localStorage.getItem("changpianId")` 读取商品 ID |
| 获取用户信息 | `GET /yonghu/session` 或对应表 session | 读取当前用户 ID |
| 订单类型下拉 | `GET /dictionary/page?dicCode=changpian_order_types` | |
| 支付类型下拉 | `GET /dictionary/page?dicCode=changpian_order_payment_types` | |
| **提交** | `POST /changpianOrder/add` | **走旧接口，不扣余额/积分** |

> **客服须知**：此页面调用的是 `/changpianOrder/add` 旧接口，与购物车结算页的 `/changpianOrder/order` 是不同的接口，行为差异很大（旧接口不扣钱）。

### 2.4 订单详情页

**文件路径**：`front/pages/changpianOrder/detail.html`

| 接口 | 用途 |
|---|---|
| `GET /changpianOrder/detail/{id}` | 获取订单详情（级联地址、商品、用户信息） |

---

## 三、接口调用方式汇总

| 后端接口 | HTTP 方法 | admin 后台 | front 前台 |
|---|---|---|---|
| `/changpianOrder/page` | GET | list.vue（分页列表） | list.html（我的订单） |
| `/changpianOrder/info/{id}` | GET | list.vue / add-or-update.vue | -- |
| `/changpianOrder/detail/{id}` | GET | -- | detail.html |
| `/changpianOrder/save` | POST | add-or-update.vue | -- |
| `/changpianOrder/update` | POST | add-or-update.vue | -- |
| `/changpianOrder/delete` | POST | list.vue | -- |
| `/changpianOrder/batchInsert` | GET | list.vue（文件导入） | -- |
| `/changpianOrder/add` | POST | -- | add.html（旧版单商品） |
| `/changpianOrder/order` | POST | -- | confirm.html（购物车结算） |
| `/changpianOrder/refund` | POST/GET | list.vue（POST） | list.html（GET） |
| `/changpianOrder/deliver` | POST | list.vue（弹窗提交） | -- |
| `/changpianOrder/receiving` | POST/GET | list.vue（POST） | list.html（GET） |
| `/changpianOrder/commentback` | POST/GET | list.vue（POST） | list.html（GET） |
| `/dictionary/page` | GET | 多处（下拉框/搜索条件） | 多处（Tab/折扣） |
| `/yonghu/session` | GET | -- | confirm.html / add.html |
| `/address/page` | GET | -- | confirm.html |
| `/barSum` | GET | list.vue（报表） | -- |

---

## 四、前端按钮显示条件 vs 后端校验差异

下表汇总前端做了但后端**没做**的权限校验，客服在接到异常反馈时可参考：

| 操作 | 前端限制 | 后端缺失 | 风险等级 |
|---|---|---|---|
| 退款 | 仅 `sessionTable=='yonghu' && userId==订单所有者` 时显示按钮 | 未校验调用者是否为订单所有者 | **高** -- 可通过 API 直接调用退他人订单 |
| 发货 | 仅 `sessionTable=='users'`（管理员）时显示按钮 | 未校验调用者是否为管理员 | **高** -- 普通用户可直接调 API 发货 |
| 收货 | 仅 `sessionTable=='yonghu' && userId==订单所有者` 时显示按钮 | 未校验调用者是否为订单所有者 | **高** -- 可通过 API 确认他人订单收货 |
| 评价 | 仅 `sessionTable=='yonghu' && userId==订单所有者` 时显示按钮 | 未校验调用者是否为订单所有者（但校验了状态==5） | **中** |
| 发货快递信息 | 前端弹窗校验非空 | 后端不校验非空 | **低** -- 可写入空快递单号 |

---

## 五、客服速查：用户操作 -> 接口 -> 数据变化

| 用户操作 | 页面 | 后端接口 | 库存 | 余额 | 积分 | 会员等级 | 购物车 |
|---|---|---|---|---|---|---|---|
| 购物车结算下单 | confirm.html | `/order` | -N | -金额 | +积分 | 可能升级 | 清除 |
| 单商品直接下单（旧） | add.html | `/add` | -N | **不变** | **不变** | **不变** | 不清除 |
| 退款（已支付/已发货） | list.html / list.vue | `/refund` | +N | +金额* | -积分* | 可能降级 | 不恢复 |
| 商家发货 | list.vue | `/deliver` | 不变 | 不变 | 不变 | 不变 | 不变 |
| 用户确认收货 | list.html / list.vue | `/receiving` | 不变 | 不变 | 不变 | 不变 | 不变 |
| 用户评价 | list.html / list.vue | `/commentback` | 不变 | 不变 | 不变 | 不变 | 不变 |

> \* 退款金额按**当前商品价格和当前折扣**计算，可能与下单时实付金额不同。
