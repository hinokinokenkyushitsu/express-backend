# 接口文档

> 统一返回格式：`{ "code": 200, "msg": "成功", "data": ..., "serverTime": "2026-05-09T12:30:45" }`
> 除登录/注册外，所有接口需在 Header 携带 `Authorization: Bearer <token>`
>
> **`serverTime` 用法**：前端做配送倒计时时应该用 `(deadline - serverTime)` 而不是
> `(deadline - 客户端本地时间)`，避免用户改本地时钟导致计时错乱。

## 通用错误码

| code | 含义 |
|------|------|
| 200 | 成功 |
| 400 | 参数错误 / 状态流转非法 / 承诺时间超出范围 |
| 401 | 未登录 / Token 无效或过期 |
| 403 | 无权操作 / 快递员被封禁 |
| 404 | 资源不存在 |
| 409 | 资源冲突（已被抢走 / 手机号已注册等） |
| 500 | 服务器内部错误 |

---

## 用户模块（同学 C）

### POST `/api/auth/register` — 注册
请求：
```json
{ "name": "张三", "phone": "13800138000", "password": "abc123", "role": "USER" }
```
约束：name ≤ 32；phone 11位手机号；password 6~64；role 可选，支持 USER / COURIER，默认 USER

### POST `/api/auth/login` — 登录
请求：
```json
{ "phone": "13800138000", "password": "abc123" }
```
响应：`data: { "token": "eyJ..." }`

> **封禁中的快递员仍可以登录、查看历史订单**。封禁仅阻止抢新单。

### GET `/api/user/profile` — 查询个人信息
响应：
```json
{
  "id": 1, "name": "张三", "phone": "13800138000",
  "avatar": "...", "role": "COURIER",
  "overdueCount": 2,
  "banUntil": null,
  "createTime": "2026-05-05T10:00:00"
}
```
注：响应**不包含**密码。
- `overdueCount`：累计超时次数（USER 始终为 0）。前端可用来在快递员页面提示"超时进度 2/5"。
- `banUntil`：封禁到期时间。`null` 或早于 `serverTime` 表示未被封禁。

### PATCH `/api/user/profile` — 更新个人信息
请求（字段都可选）：
```json
{ "name": "李四", "avatar": "https://..." }
```

---

## 订单模块

### POST `/api/order` — 创建订单（同学 C）
请求：
```json
{
  "stationAddress": "菜鸟驿站(北门店)",
  "targetAddress": "宿舍楼A-301",
  "pickupCode": "8-2-3456",
  "amount": 5.00,
  "urgency": 1
}
```
- `urgency`：`0` 普通（默认）/ `1` 紧急。决定快递员可承诺的最长送达时间。

### GET `/api/order/my` — 我的订单（同学 D）
查询参数：`pageNum=1&pageSize=10`

响应（带发单人和快递员信息）：
```json
{
  "code": 200,
  "serverTime": "2026-05-09T12:30:45",
  "data": {
    "records": [
      {
        "order": {
          "id": 101, "userId": 1, "courierId": 5,
          "stationAddress": "...", "targetAddress": "...",
          "pickupCode": "...", "amount": 5.00,
          "status": 1,
          "urgency": 1,
          "promisedMinutes": 25,
          "grabTime": "2026-05-09T12:10:00",
          "deadline": "2026-05-09T12:35:00",
          "overdue": 0,
          "createTime": "..."
        },
        "user":    { "id": 1, "name": "张三", "phone": "138...", "role": "USER" },
        "courier": {
          "id": 5, "name": "小王", "phone": "139...", "role": "COURIER",
          "overdueCount": 2, "banUntil": null
        }
      }
    ],
    "total": 12, "size": 10, "current": 1, "pages": 2
  }
}
```

**前端倒计时关键字段**：
| 字段 | 说明 |
|------|------|
| `order.deadline` | 服务端算好的截止时刻；倒计时 = deadline - serverTime |
| `order.overdue` | 已超时返回 1；前端应改用红色 + 显示"已超时（免单）" |
| `order.amount` | 一旦 overdue=1，金额会被服务端清零，前端直接展示即可 |
| `order.urgency` | 用于在订单卡片上展示"紧急"标签 |

### GET `/api/order/hall` — 抢单大厅（同学 D）
仅返回 `status=0` 的订单，**紧急单优先展示**，再按 `createTime` 正序。响应结构同上。

### POST `/api/order/{orderId}/grab` — 抢单（同学 D）
请求 body：
```json
{ "promisedMinutes": 25 }
```
约束：
- 紧急单：`1 ≤ promisedMinutes ≤ 30`
- 普通单：`1 ≤ promisedMinutes ≤ 120`
- 超出范围 → 400 `order.promised.minutes.invalid`

错误：
- 403 `courier.banned` —— 当前快递员被封禁中
- 409 `order.grab.taken` —— 该订单已被别人抢走
- 409 `order.grab.too.fast` —— 同一订单短时间重复请求

### PATCH `/api/order/{orderId}/status?status=2` — 更新状态（同学 D）

状态流转规则：

| 当前状态 | 允许的目标状态 |
|---------|--------------|
| 0 待抢单 | 1（抢单接口）, 3（取消） |
| 1 配送中 | 2（已完成） |
| 2 已完成 | （终态） |
| 3 已取消 | （终态） |

权限：仅接单快递员或管理员

**完成订单时的隐性副作用**：
当快递员把状态改为 `2 已完成` 时，如果当前已超过 `deadline`，
服务端会原子地：
1. 把 `overdue` 设为 1
2. 把 `amount` 清零（用户无需支付）
3. 给该快递员的 `overdueCount` 累加 1
4. 累加后达到 5 → 触发封禁 7 天，`overdueCount` 清零，`banUntil` 写入

这些都通过 SQL CAS 保证只发生一次。

### DELETE `/api/order/{orderId}` — 取消订单（同学 D）
权限：发单人本人；约束：仅 status=0 可取消

---

## 后台机制

### 超时订单兜底扫描
每 5 分钟一次，扫描所有 `status=配送中` 且 `overdue=0` 且 `deadline<NOW()` 的订单，
执行与"完成时超时"相同的结算逻辑。
**目的**：防止快递员故意拖着不点完成而让用户卡在等待状态。

### 封禁规则
- 阈值：累计超时 **5** 次
- 封禁时长：**7** 天
- 计数在被封禁后清零，期满后重新累计
- 封禁期间：可登录、可查看历史订单，但不能抢新单（`POST /api/order/{id}/grab` 返回 403）
