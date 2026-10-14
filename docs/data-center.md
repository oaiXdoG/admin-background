# 数据中心

设计背景见 `log-service-design.md`。日志由业务服务通过 log-sdk 写入 ClickHouse 的 `<库>.event` 表，admin 只读查询。

## 一、日志存储配置（系统设置 → 日志存储）

配置保存在 `system_setting` 的 `logStorage.*` 键里；密码用 `security.secret-key`（`conf/application.properties`）经 AES-GCM 加密保存，接口不返回明文。修改密钥后已保存的密码无法解密，需要重新填写。

| 键 | 说明 |
| --- | --- |
| `logStorage.url` | ClickHouse HTTP 地址，例如 `http://111.230.143.165:38123` |
| `logStorage.database` | 库名：内网后台填 `logs_test`，正式后台填 `logs_prod` |
| `logStorage.readUser` / `readPassword` | 只读账号，数据中心查询使用 |
| `logStorage.adminUser` / `adminPassword` | 管理账号，只用于修改保留期，可不配置 |

### `POST /api/system/log-storage/get`

权限：平台 `manage_setting`。请求体 `{}`。

```json
{ "url": "http://111.230.143.165:38123", "database": "logs_test", "readUser": "reader_test", "readPasswordSet": true, "adminUser": "logs_admin", "adminPasswordSet": true }
```

### `POST /api/system/log-storage/update`

权限：`manage_setting:storage`。返回同 `get`。

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| url | 是 | `http://` 或 `https://` 开头，最长 256 |
| database | 是 | 字母、数字、下划线，字母或下划线开头 |
| readUser | 是 | 字母、数字、`_.-`，最长 64 |
| readPassword | 首次必填 | 留空表示沿用已保存的密码 |
| adminUser | 否 | 留空表示不配置管理账号（同时清除管理密码） |
| adminPassword | 配置管理账号时首次必填 | 留空表示沿用 |

### `POST /api/system/log-storage/test`

权限：`manage_setting:storage`。请求体同 `update`，用表单里的值测试（密码留空时用已保存的密码），不保存。

```json
{ "readOk": true, "readMessage": null, "adminOk": true, "adminMessage": null, "version": "25.8.1.1", "retentionDays": 90 }
```

`adminOk` 在未配置管理账号时为 `null`；`retentionDays` 从表的 TTL 读出，读不到时为 `null`。连接失败不是业务错误，结果在 `readOk` / `readMessage` 里。

### `POST /api/system/log-storage/retention`

权限：`manage_setting:storage`。请求 `{ "retentionDays": 180 }`（1～3650），使用已保存的管理账号执行：

```sql
ALTER TABLE <库>.event MODIFY TTL toDateTime(time) + INTERVAL 180 DAY DELETE WHERE retention = 'normal'
```

返回 `{ "retentionDays": 180 }`。永久保存的日志（`retention = 'permanent'`）不受影响。

## 二、查询模板

表 `query_template`，按项目保存。一个模板对应一个日志事件，字段映射决定结果列和可用的查询条件。

| 字段 | 说明 |
| --- | --- |
| category | `data`（数据查询页面使用）或 `log`（日志查询页面使用） |
| name | 项目内唯一，最长 64 |
| event | 事件名，与 log-sdk 规则相同：小写字母开头，小写字母、数字、下划线，最长 64 |
| fields | 字段映射 JSON 数组（文本），1～100 个，见下 |

字段映射的每一项：

| 属性 | 说明 |
| --- | --- |
| key | 结果列的标识，字母、数字、下划线，模板内唯一 |
| label | 列标题，最长 64 |
| source | 取值来源：日志自带列 `time`、`server`、`actor`、`event`、`logId`、`retention`，或业务字段路径 `data.<字段>[.<子字段>…]`（最多 8 段） |
| type | `string`、`number`、`boolean`、`time`、`json`、`jsonText`。`time` 列只能是 `time` 类型，其余自带列只能是 `string` 或 `number` |
| 其他 | 例如 `json`（JSON 子字段的展示映射），服务器原样保存，只给前端使用 |

### `POST /api/data/template/list`

请求 `{ "projectId": 1, "category": "log" }`。`category` 为空时是模板管理页面，权限 `data_template`；为 `data` / `log` 时权限分别是 `data_query` / `data_log`。

```json
{ "database": "logs_test", "list": [ { "id": 1, "category": "log", "name": "支付成功", "event": "pay_success", "fields": "[...]", "createdAt": 0, "updatedAt": 0 } ] }
```

`database` 为当前连接的库名，未配置时为空字符串，页面用来提示当前查询的环境。

### `POST /api/data/template/create`、`update`、`remove`

权限：`data_template:create`、`:update`、`:remove`。

- create：`{ projectId, category, name, event, fields }`，返回模板
- update：`{ projectId, templateId, category, name, event, fields }`，返回模板
- remove：`{ projectId, templateId }`

## 三、查询

### `POST /api/data/query`

权限：按模板分类，`data_query` 或 `data_log`。

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| projectId | 是 | 必须是会话当前项目 |
| templateId | 是 | 必须属于该项目 |
| startTime / endTime | 是 | 毫秒时间戳，范围 `[startTime, endTime)` |
| relation | 有条件时必填 | `and` 或 `or` |
| conditions | 否 | 最多 20 个：`{ field, operator, value, value2 }` |
| page / pageSize | 否 | 默认 1 / 20；pageSize 最大 200，偏移量最大 10000 |

条件：`field` 为模板字段的 `key`；值都以文本传递。

| 字段类型 | operator | value |
| --- | --- | --- |
| number | eq、ne、gt、ge、lt、le、between | 数字；between 时 value2 为上限（含） |
| time | 同上 | 毫秒时间戳 |
| boolean | eq、ne | `true` / `false` |
| string、json、jsonText | eq、ne、contains（不区分大小写） | 文本 |

```json
{ "database": "logs_test", "total": 1, "list": [ { "logId": "01M4...", "time": 1760410116381, "server": "game-1", "actor": "1001", "event": "pay_success", "retention": "permanent", "data": { "orderId": "A20261014001", "amount": 6.0 } } ] }
```

实现约定：

- 服务器按本次校验通过的项目填入 App Key 和模板的事件名，请求不能指定其他项目或事件。
- 条件值和 JSON 路径都通过 ClickHouse 查询参数（`{name:Type}`）传递，不拼接进 SQL。
- 查询使用只读账号，设置 `readonly=2`、`max_execution_time=15`，按时间倒序分页。
- 未配置日志存储返回 `100042`；ClickHouse 不可用或查询失败返回 `100043`，原因记在服务器日志里。

## 四、后台操作日志

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
