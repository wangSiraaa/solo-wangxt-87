# 渔业合作社配额账本（演示系统）

> **免责声明**：本系统使用**虚构物种与许可规则**，不连接任何监管系统，**不作为真实捕捞许可**。
> 物种（蓝鳍云鲷、银鳞星鳗、紫背霞虾）、海区（北屿海区、东礁海区）、渔汛与船舶均为虚构。

按 **船舶 × 物种 × 海区 × 季节** 核对捕捞配额的工作台：React 前端 + Spring Boot 服务 + PostgreSQL 配额账本，重量一律 `BigDecimal(19,3)`（千克，精确到克）。

## 核心设计

**账本即事实**：`quota_account` 上没有任何余额字段。余额永远由不可变的 `ledger_entry` 条目汇总：

```
可用余额 = Σ 全部条目
配额口径 = ALLOCATION + TRANSFER_IN + TRANSFER_OUT
在途占用 = -(RESERVATION + RESERVATION_RELEASE)
实捕核销 = -ACTUAL_DEDUCTION
```

**业务流**：航次申报（RESERVATION 占用预计额度）→ 靠港卸货登记（PENDING，**不落账本、不计入实捕**）→ 称重核实（释放本票预计占用 + 按实重 ACTUAL_DEDUCTION；预计>实捕时差额自动回到可用余额）→ 结案释放剩余占用。

**规则落实**：

| 要求 | 实现 |
|---|---|
| 物种/海区/季节不匹配不得顶替 | 账户按四元组唯一定位；申报/核销只触达精确匹配账户；调拨强制同维度 |
| 调拨保存来源及生效期间 | `quota_transfer` 持久化 from/to 账户与 effectiveFrom~effectiveTo，过账写一出一进两条条目，期间外拒绝 |
| 待确认称重不计入最终捕捞量 | PENDING 卸货单不产生任何账本条目；存在 PENDING 时禁止结案 |
| 同一凭证多次回传不重复扣减 | `landing.receipt_no` 全局唯一 + 建单/核实双重幂等（同凭证同重量重复核实直接返回） |
| 删除草稿不丢捕捞事实 | 仅 DRAFT 可删（草稿从未落账本）；已申报航次禁止删除；已核实卸货与实扣条目永久保留 |
| 从余额追到航次和调拨 | `GET /api/accounts/{id}/ledger` 返回带单据类型/编号的明细，前端可点击跳转航次 |
| 实捕超预计 | 超出占用的部分须有余额兜底，否则核实被拒绝 |

## 运行

```bash
# 全栈（PostgreSQL + 后端 + 前端）
docker compose up --build
# 前端 http://localhost:8081 ，后端 http://localhost:8080

# 或本地开发
docker compose up db                  # 仅数据库
cd backend && mvn spring-boot:run     # 后端 :8080（自动播种虚构演示数据）
cd frontend && npm install && npm run dev   # 前端 :5173（代理 /api）
```

## 样例验证

- **集成测试**（H2 的 PostgreSQL 兼容模式，生产配置始终为 PostgreSQL）：
  `cd backend && mvn test`
  覆盖：预计>实捕差额释放、同船多港卸货、两航次争用额度（后者被拒）、凭证重复回传幂等、待确认不计入实捕、删除草稿保留事实、跨维度顶替拒绝、调拨留痕与生效期、超捕余额兜底。
- **界面一键验证**：前端「样例验证」页 → `POST /api/demo/run`，返回逐步日志。

## API 摘要

```
GET    /api/meta                        基础数据 + 免责声明
GET    /api/accounts                    全部账户余额视图（配额/占用/实捕/可用）
GET    /api/accounts/{id}/ledger        账本明细（追溯到航次/卸货/调拨）
POST   /api/voyages                     建草稿
POST   /api/voyages/{id}/declare        申报（占用预计额度，争用时余额不足即拒）
POST   /api/voyages/{id}/landings       登记卸货（按 receiptNo 幂等）
POST   /api/voyages/landings/{id}/verify  核实（转实扣/释放差额，重复回传幂等）
POST   /api/voyages/{id}/close|cancel   结案 / 取消
DELETE /api/voyages/{id}                删除草稿（仅限 DRAFT）
POST   /api/transfers                   调拨（同维度 + 生效期间校验，来源留痕）
POST   /api/demo/run                    运行样例场景
```

业务规则冲突一律返回 `409 {"error": "..."}`。
