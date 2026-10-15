# 日志中心

设计背景见 `log-service-design.md`。日志由业务服务通过 log-sdk 写入 ClickHouse 的 `<库>.event` 表，admin 只读查询。业务服务器自身的数据（玩家资料、订单等）在数据中心查询，见 `data-center.md`。

ClickHouse 的连接分两层：

- **日志源**（项目级，`日志中心 → 日志源`）：本项目查询日志用的只读连接，**每个项目一个**。ClickHouse 即使是集群，对外也只有一个入口（Distributed 表），不需要多个日志源；正式和测试是两套后台，各连各的库。
- **日志保留期**（平台级，`系统设置 → 日志保留期`）：管理账号，只用来修改表的 TTL 保留期，不参与查询。

查询都强制带上本项目的 App Key，只能查到本项目的日志。

## 一、日志源

表 `log_source`，`project_id` 唯一。密码用 `security.secret-key`（`conf/application.properties`）经 AES-GCM 加密保存，接口不返回明文；修改密钥后已保存的密码无法解密，需要重新填写。

| 字段 | 说明 |
| --- | --- |
| url | ClickHouse HTTP 地址，`http://` 或 `https://` 开头，最长 256，例如 `http://111.230.143.165:38123`；末尾的 `/` 会去掉 |
| database | 库名：内网填 `logs_test`，正式填 `logs_prod`；字母、数字、下划线，字母或下划线开头 |
| readUser / readPassword | 只读账号（只需要 SELECT 权限）；首次保存密码必填，之后留空沿用 |

地址解析出的 IP 不能是链路本地（含云元数据 `169.254.169.254`）、组播或通配地址，HTTP 不跟随跳转。

| 接口 | 权限 | 请求 / 返回 |
| --- | --- | --- |
| `POST /api/log/source/get` | 项目 `log_source` | `{ projectId }` → `{ source }`，未配置时为 `null`，否则 `{ url, database, readUser, readPasswordSet, updatedAt }` |
| `POST /api/log/source/save` | `log_source:update` | `{ projectId, url, database, readUser, readPassword }` → 日志源，新增或覆盖 |
| `POST /api/log/source/test` | `log_source:update` | 请求同 save → `{ version }`；用表单里的值测试，密码留空时用已保存的密码，不保存 |

错误码：`100064` 连接失败（原因附在信息里）；项目没有日志源时查询返回 `100042`。

## 二、日志保留期（系统设置）

保存在 `system_setting` 的 `logStorage.url`、`logStorage.database`、`logStorage.adminUser`、`logStorage.adminPassword`（加密）。

| 接口 | 权限 | 说明 |
| --- | --- | --- |
| `POST /api/system/log-storage/get` | 平台 `manage_setting` | 返回 `{ url, database, adminUser, adminPasswordSet }` |
| `POST /api/system/log-storage/update` | `manage_setting:storage` | `{ url, database, adminUser, adminPassword }`，密码留空沿用 |
| `POST /api/system/log-storage/test` | `manage_setting:storage` | 请求同 update，不保存；返回 `{ adminOk, adminMessage, version, retentionDays }`，连接失败不是业务错误，结果在 `adminOk` / `adminMessage` |
| `POST /api/system/log-storage/retention` | `manage_setting:storage` | `{ retentionDays }`（1～3650），返回 `{ retentionDays }` |

修改保留期执行：

```sql
ALTER TABLE <库>.event MODIFY TTL toDateTime(time) + INTERVAL 180 DAY DELETE WHERE retention = 'normal'
```

永久保存的日志（`retention = 'permanent'`）不受影响。未配置管理账号返回 `100044`。

## 三、页面

| 页面 | 菜单 | 用途 |
| --- | --- | --- |
| 日志查询 | `log_query` | 按时间、事件（多选）、用户、服务器、关键字查；选了事件就可以按字段加条件（且/或，多选时可选字段是各事件字段的合集）；只选一个事件时结果按字段成列；点用户 ID 即用户轨迹，点行看详情；导出按钮 `log_query:export` |
| 事件管理 | `log_event` | 列出最近有数据的事件和已配置说明的事件（条数、最后上报）；编辑中文名、说明和字段说明（`log_event:update`），删除说明（`log_event:remove`），跳到日志查询 |
| 日志源 | `log_source` | 本项目的日志源（一张表单），保存按钮 `log_source:update` |

**用户与直播间约定**：`actor` 统一叫「用户」，主播和玩家都是用户，用事件名前缀区分（`anchor_*`、`player_*`）。直播间里的所有事件（包括主播自己的）都在 data 里带同名字段 `anchorId`，这样「某直播间的所有事件」「某直播间里某玩家的充值」都能用 `anchorId` 条件（加上「用户」）查出来，可多选事件。开播不区分场次（有断线重连）。场景验证见 admin-front `docs/log-query-room-cases.md`。

**字段约定**：事件上报的 data 字段只增不改不删（不改名、不改类型、不改含义）。新增字段时旧日志里该字段为空，按它加的条件不命中旧日志。确实需要不兼容的变化时换新事件名（例如 `pay_success_v2`）。

事件说明只影响展示：事件和字段显示中文名，结果列按配置的字段和顺序。没有配置的事件也能查，字段从最近的日志里自动读取。

导出最多 5 万行，前端生成 CSV（带 BOM，Excel 可直接打开），导出操作记入操作日志。统计视图（按天/字段分组的计数、求和等）留到下一阶段。

## 四、事件字典

表 `log_event`，按项目和事件名唯一。

| 字段 | 说明 |
| --- | --- |
| event | 事件名，与 log-sdk 规则相同：小写字母开头，小写字母、数字、下划线，最长 64 |
| label | 中文名，可空，最长 64 |
| description | 说明，可空，最长 256 |
| fields | 字段说明 JSON 数组（文本），最多 200 项：`{ path, label, type }` |

字段说明的 `path` 是 data 里的路径（点号分隔，最多 8 段，不带 `data.` 前缀，不能重复）；`label` 可空，最长 64；`type` 为 `string`、`number`、`boolean`、`time`、`json`、`jsonText`。不合法返回 `100047`。

| 接口 | 权限 | 请求 / 返回 |
| --- | --- | --- |
| `POST /api/log/event/dict/list` | 项目 `log_query` 或 `log_event` | `{ projectId }` → `{ list }`，每项 `{ event, label, description, fields }` |
| `POST /api/log/event/dict/save` | `log_event:update` | `{ projectId, event, label, description, fields }`，按事件名新增或覆盖 |
| `POST /api/log/event/dict/remove` | `log_event:remove` | `{ projectId, event }`，只删说明，不删日志 |

### 事件发现

`POST /api/log/event/list`，权限 `log_query` 或 `log_event`。请求 `{ projectId, days }`（days 默认 7，1～90），最多 500 个，按条数倒序。

```json
{ "list": [ { "event": "pay_success", "total": 12034, "lastTime": 1760410116381 } ] }
```

`POST /api/log/event/fields`，权限同上。请求 `{ projectId, event, days }`。在最近 days 天内最多 1000 条日志上执行 `distinctJSONPathsAndTypes(data)`，样例取最新一条。

```json
{ "fields": [ { "path": "amount", "types": ["Float64", "Int64"] }, { "path": "order.level", "types": ["Int64"] } ], "sample": { "amount": 6.0, "order": { "level": 3 } } }
```

前端按类型推荐展示类型：数组/对象为 JSON，有文本为文本（样例像日期则为日期时间），布尔为布尔值，其余数字。

## 五、查询

### `POST /api/log/query`

权限：`log_query`。

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| projectId | 是 | 必须是会话当前项目 |
| startTime / endTime | 是 | 毫秒时间戳，范围 `[startTime, endTime)` |
| events | 否 | 为空表示全部事件，最多 50 个 |
| actor / server | 否 | 精确匹配，最长 128 |
| keyword | 否 | 在 data 的 JSON 文本里不区分大小写搜索，最长 128 |
| relation | 有条件时必填 | `and` 或 `or`，只作用于 conditions 之间 |
| conditions | 否 | 最多 20 个：`{ path, type, operator, value, value2 }` |
| page / pageSize | 否 | 默认 1 / 20；pageSize 最大 200，偏移量最大 10000 |

条件的 `path` 为自带列 `time`、`server`、`actor`、`event`、`logId`、`retention`（`time` 只能是 time 类型，其余只能是 string 或 number），或业务字段 `data.<字段>[.<子字段>…]`（最多 8 段）。值都以文本传递：

| 类型 | operator | value |
| --- | --- | --- |
| number | eq、ne、gt、ge、lt、le、between | 数字；between 时 value2 为上限（含） |
| time | 同上 | 毫秒时间戳 |
| boolean | eq、ne | `true` / `false` |
| string、json、jsonText | eq、ne、contains（不区分大小写） | 文本 |

取值规则（`LogQueryService.expression`）：

- 字段不存在、为 null 或类型对不上（例如按是/否查但值是文本）时取 NULL，**任何条件都不命中**，包括「不等于」。
- 数字字段也接受数字文本（`"100"` 按 100 比较）。整数超过 2^53 时按数字比较会丢精度，订单号、玩家 ID 这类字段请设为文本类型。
- 时间可以是毫秒数，也可以是日期文本；不带时区的文本按 `Asia/Shanghai` 解析（与 `time` 列一致，ClickHouse 服务器本身是 UTC），带 `Z` 或偏移量的按其时区。
- 文本「包含」不是 LIKE，`%`、`_` 按普通字符匹配；数组和对象按 JSON 文本匹配。
- 验证数据和 36 个场景见 admin-front `docs/log-query-test-cases.md`，生成脚本在 `.ops/qa_log.py`（seed / verify / clean）。

```json
{ "database": "logs_test", "total": 1, "list": [ { "logId": "01M4...", "time": 1760410116381, "server": "game-1", "actor": "1001", "event": "pay_success", "retention": "permanent", "data": { "orderId": "A20261014001", "amount": 6.0 } } ] }
```

### `POST /api/log/export`

权限：`log_query:export`（同时需要 `log_query`）。请求同查询但不分页，按时间倒序最多返回 5 万行，查询超时 60 秒。

实现约定：

- 服务器按本次校验通过的项目填入 App Key，请求不能指定其他项目。
- 条件值、事件列表和 JSON 路径都通过 ClickHouse 查询参数（`{name:Type}`）传递，不拼接进 SQL。
- 查询使用日志源的只读账号，设置 `readonly=2`、`max_execution_time=15`（导出 60），按时间倒序。
- 条件不合法返回 `100048`；ClickHouse 不可用或查询失败返回 `100043`，原因记在服务器日志里。

## 六、后台操作日志

后台自己的操作日志不走 log-sdk，直接写 admin-background 的 PostgreSQL 表 `operation_log`，与上面的日志存储无关（日志存储只用来读取外部业务服务写入 ClickHouse 的日志）。在平台页面「系统管理 → 操作日志」（`manage_operation_log`）查询。

- 记录范围：登录、退出和所有写操作（清单见 `OperationLogService.ACTIONS`），查询类接口不记录。业务失败也记录，`success=false` 并带错误码。记录失败只打印告警，不影响业务请求。
- 表字段：`account_id`（登录失败时为空）、`account`（操作者登录名；登录时为请求里的登录名）、`route`、`action`、`success`、`code`（0 或业务错误码）、`params`、`created_at`。
- `params` 为请求参数 JSON：名称含 password 的字段记为 `******`，logo、favicon、avatar 记为 `(图片)`，超过 8000 字符截断。
- 目前不自动清理，数据量大时再按 `created_at` 定期删除。

### `POST /api/system/operation-log/list`

权限：平台 `manage_operation_log`。

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| startTime / endTime | 是 | 毫秒时间戳，范围 `[startTime, endTime)` |
| account | 否 | 登录名，精确匹配 |
| action | 否 | 操作名称，模糊匹配 |
| success | 否 | `true` / `false`，为空不限 |
| page / pageSize | 否 | 默认 1 / 20；pageSize 最大 200，偏移量最大 10000 |

```json
{ "total": 1, "list": [ { "id": 12, "time": 1760410116381, "accountId": 1, "account": "admin", "action": "修改菜单", "route": "/api/menu/update", "success": true, "code": 0, "params": { "menuId": 10002, "name": "用户管理" } } ] }
```
