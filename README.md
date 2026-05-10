# 快递代取系统（模块化单体架构）

## 为什么是模块化单体？

我们小组项目规模（5 人，单学期）不需要拆分微服务，但需要清晰的模块边界。
**模块化单体（Modular Monolith）**是工业界对小团队/中等项目最推荐的折中方案：

- ✅ **一个项目、一次部署、一个数据库** —— 联调和部署都简单
- ✅ **代码按业务模块严格分包** —— C 改 user/，D 改 order/，几乎不会冲突
- ✅ **强制模块间通过显式 API 通信** —— 通过 `UserFacade` 接口调用，禁止偷偷依赖内部
- ✅ **可演进性** —— 哪天某个模块流量起飞，按模块边界一刀切就能拆成微服务

## 项目结构

```
src/main/java/com/fiveok/express/
├── ExpressApplication.java          # 启动类（开启 @EnableScheduling）
│
├── base/                            # 共享基础设施（组长 E 维护）
│   ├── config/                      #   JWT 过滤器、MyBatis-Plus 配置
│   ├── exception/                   #   业务异常 + 全局处理器
│   └── utils/                       #   Result, JwtUtil, RedisLockUtil
│
├── user/                            # 用户模块（同学 C）
│   ├── api/                         #   ⭐ 对外门面（UserFacade, UserView）
│   ├── controller/                  #   注册、登录、个人信息接口
│   ├── service/                     #
│   ├── mapper/                      #   含封禁判断 + 超时累加 SQL
│   ├── entity/                      #
│   ├── dto/                         #
│   └── enums/                       #
│
└── order/                           # 订单模块（同学 D 主导，含 C 写的 createOrder）
    ├── controller/                  #   订单全部接口
    ├── service/                     #
    ├── scheduler/                   #   ⭐ 超时订单定时结算
    ├── mapper/                      #   含抢单原子 SQL + 超时 CAS 标记
    ├── entity/
    ├── dto/                         #   含 OrderView（带用户信息的视图）
    └── enums/                       #   OrderStatus + OrderUrgency
```

## 模块边界规则

模块化单体的灵魂是**严格的边界**。本项目规则如下：

| 规则 | 说明 |
|------|------|
| ✅ 任何模块都可以依赖 `base` | base 是公共基础设施 |
| ✅ `order` 可以依赖 `user.api` | 通过 `UserFacade` 跨模块调用 |
| ❌ `order` 禁止依赖 `user` 内部包 | `user.entity`、`user.mapper`、`user.service` 都不能 import |
| ❌ `user` 禁止依赖 `order` | 基础模块不能反向依赖 |
| ❌ `base` 禁止依赖业务模块 | 基础设施保持纯净 |

**这些规则不是靠"自觉遵守"，而是写成自动化测试**：见 `src/test/java/.../architecture/ModuleBoundaryTest.java`，使用 ArchUnit 库，每次跑测试都会检查。**任何违反都会让 CI 红灯**。

## 跨模块调用示例

订单大厅需要展示"发单人姓名/头像"——这就是典型的跨模块协作。看 `OrderServiceImpl#enrichWithUser`：

```java
// order 模块只 import user.api 包
import com.fiveok.express.user.api.UserFacade;
import com.fiveok.express.user.api.UserView;

// 一次批量查询，避免 N+1
Map<Long, UserView> userMap = userFacade.findByIds(userIds);
```

`UserFacade` 是 user 模块对外承诺的"契约"。
即使 user 模块内部从 MyBatis 改成 JPA、从 MySQL 迁到别的数据库、甚至拆成独立微服务，
只要 `UserFacade` 接口签名不变，order 模块代码不需要任何修改。
**这就是边界的价值。**

> 抢单流程也是经典的跨模块协作示例：
> order 模块在抢单前调 `userFacade.isCourierBanned(courierId)` 做封禁校验，
> 在结算超时时调 `userFacade.recordCourierOverdue(...)`，
> 全程不知道 user 模块用的是什么 ORM、表结构如何。

## 时效与超时机制（v3 新增）

### 设计目标
- 用户下单时能选择**紧急 / 普通**
- 快递员抢单时**承诺一个送达时间**
- 双端实时显示**倒计时**
- 超时则**用户免单**
- 快递员**累计 5 次超时 → 封禁 7 天**

### 关键决策与权衡

| 问题 | 选择 | 理由 |
|------|------|------|
| 承诺时间能不能任意大？ | 紧急≤30，普通≤120 | 否则快递员承诺 9999 分钟就永远不超时，机制失效 |
| 超时认定时机 | 完成时检测 + 每 5 分钟兜底扫描 | 单靠完成事件会被快递员"故意不点完成"绕过 |
| 同一单会不会被结算两次？ | 否，`overdue=0→1` 的 CAS | 主动完成与定时扫描可能并发，必须幂等 |
| 累加 5 次时多个超时事件并发怎么办？ | `WHERE overdue_count >= threshold` 的 CAS | 只有第一个 SQL 会成功，避免重复封禁 |
| 倒计时用本地时钟还是服务端时钟？ | 服务端 `serverTime` + `deadline` | 防止用户改本地时钟 |
| 大厅排序 | 紧急单优先 + 时间正序 | 紧急单值得被先看到 |

### 状态流与超时事件

```
                  promisedMinutes
   PENDING ─grab─► IN_PROGRESS ─complete─► COMPLETED
                       │
                       │ 完成时若 NOW > deadline
                       │ 或定时任务发现 NOW > deadline
                       ▼
                   overdue=1, amount=0
                   courier.overdue_count++
                                │
                                │ 达到 5 次
                                ▼
                       ban_until = NOW + 7 天
                       overdue_count 清零
```

## 分工对应关系

| 同学 | 负责区域 |
|------|----------|
| C | `user/` 整个模块（含封禁/超时累加 Facade）+ `order/` 中的 `createOrder` 业务方法 + `schema.sql` |
| D | `order/` 中除 createOrder 外的所有业务（查询/抢单/状态/取消/超时结算/定时任务）+ `db-permissions.sql` |
| E | `base/` 共享基础设施 + `ModuleBoundaryTest` 架构约束 |

## 启动方式

```bash
# 1. 设置环境变量
export DB_USERNAME=express_app
export DB_PASSWORD=你的密码
export JWT_SECRET=至少32字符的密钥
export REDIS_PASSWORD=

# 2. 初始化数据库（C 的产出）
mysql -u root -p < src/main/resources/schema.sql

# 3. 配置数据库权限（D 的产出）
mysql -u root -p < src/main/resources/db-permissions.sql

# 4. 启动 Redis
redis-server

# 5. 启动应用
mvn spring-boot:run
```

## API 接口

| 方法 | 路径 | 负责人 | 说明 |
|------|------|:---:|------|
| POST | `/api/auth/register` | C | 注册 |
| POST | `/api/auth/login` | C | 登录 |
| GET | `/api/user/profile` | C | 查询个人信息（含 overdueCount/banUntil） |
| PATCH | `/api/user/profile` | C | 更新个人信息 |
| POST | `/api/order` | C | 创建订单（含 urgency） |
| GET | `/api/order/my` | D | 我的订单（带用户信息和倒计时字段） |
| GET | `/api/order/hall` | D | 抢单大厅（紧急单优先） |
| POST | `/api/order/{id}/grab` | D | 抢单（body 含 promisedMinutes） |
| PATCH | `/api/order/{id}/status` | D | 状态流转（CAS，完成时检测超时） |
| DELETE | `/api/order/{id}` | D | 取消订单 |

详细接口文档见 [docs/API.md](docs/API.md)。

## 团队协作的实际工作流

> 想象 C 和 D 在 GitHub 上协作

1. **C 改 user 包** → 提交 PR → 几乎不会和 D 冲突（不同目录）
2. **D 改 order 包** → 提交 PR → 同上
3. **D 想用用户信息** → 不能直接改 user 内部 → **找 C 商量加 UserFacade 接口**
   （比如这次 v3 加的 `isCourierBanned` 和 `recordCourierOverdue` 就是 D 推动 C 在 facade 上加的）
4. **CI 自动跑 ModuleBoundaryTest** → 一旦有人偷偷跨模块依赖，立刻报错

这就是真实工作中的"有边界的协作"，和你们之前担心的"两个项目分开做"相比，更接近实际工程实践。
