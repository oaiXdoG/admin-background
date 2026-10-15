# 日志系统设计

状态：已确认（第三版）。第 1～4 步已实施，日志中心接口见 `log-center.md`（原「数据中心」已拆分：日志在日志中心，业务数据在数据中心 `data-center.md`）。第 5 步改为：admin 自己的操作日志不走 log-sdk，存 PostgreSQL 表 `operation_log`（见 `log-center.md` 第六节）。

## 一、目标与约束

| 项 | 约定 |
| --- | --- |
| 数据量 | 每天 100 万条以内，峰值按每秒几百条估算 |
| 接入方 | Java 业务服务，不限定框架（Spring、纯 zfoo、普通 main 程序都可以） |
| 隔离 | 按项目隔离：每条日志带项目的 App Key。App Key 由平台生成或后台自定义，项目创建后不可修改 |
| 环境 | 内网（测试）和正式环境的数据完全分开 |
| 实时性 | 秒级可查 |
| 保留 | 普通日志默认 3 个月，可修改；支付类日志永久保存 |
| 解耦 | 业务方只依赖 log-sdk；日志链路任何部分故障都不影响业务 |
| 查询 | admin 后台的数据中心按「事件 + 字段结构」配置查询模板 |

## 二、整体架构

```
业务服务（引入 log-sdk，启动时 LogReporter.init(...)）
   │  LogReporter.event("pay_success").permanent().actor(uid).put("amount", 6.0).send()
   │  异步追加写本地文件，每行一条 JSON，按小时切分
   ▼
本机磁盘（logDir）                          ← 缓冲：网络、ClickHouse 故障时日志在这里攒着
   ▼
Vector（现成软件，每台业务机器装一个）       ← 读新增行、校验、批量写入、断点续传
   ▼
ClickHouse（现成软件，gz 服务器 Docker）     ← 日志数据库：logs_test / logs_prod 两个库
   ▲  JDBC，只读账号，参数化 SQL
admin-background 数据中心模块               ← 事件定义、查询模板、日志查询、保留期；登录与项目权限
   ▲
admin-front 数据中心
```

- 需要写代码的只有 log-sdk 和 admin 的数据中心模块；ClickHouse 和 Vector 是现成软件，只需安装和配置。
- 不需要消息队列，也不需要独立的日志服务进程。以后要增加实时告警等第二个消费方时，可以在 Vector 和 ClickHouse 之间插入 Kafka，业务方和 log-sdk 都不用改。

## 三、三份配置，各自独立

| 配置 | 谁读 | 在哪 | 内容 |
| --- | --- | --- | --- |
| log-sdk 初始化参数 | 业务服务里的 log-sdk | 业务方自己决定从哪读，启动时传给 `LogReporter.init` | App Key、服务器标识、日志目录、本地保留时长 |
| Vector 配置 | 业务机器上的 Vector | 该机器的 `vector.yaml` | 读取的日志目录、目标 ClickHouse 地址、库名、写入账号 |
| ClickHouse 配置 | gz 上的 ClickHouse | gz 服务器 | 内存上限、账号、库表结构 |

唯一需要对齐的地方：log-sdk 的日志目录与 Vector 读取的目录一致。业务代码不知道 ClickHouse 和 Vector 的存在；环境区分由每台机器的 Vector 配置决定（写入 `logs_test` 还是 `logs_prod`）。

## 四、项目 log-sdk

新建仓库 `log-sdk`，工程结构与 admin-background 一致（Maven、zfoo、相同开发规范）：

| 目录 | 内容 |
| --- | --- |
| `src/` | log-sdk 本身，打成 jar 供业务服务引入 |
| `deploy/clickhouse/` | Docker 启动命令、内存和账号配置、建库建表 SQL |
| `deploy/vector/` | Vector 配置模板和安装说明 |

`deploy` 不是程序，也不打进 jar，是部署 ClickHouse 和 Vector 时照着执行的配置文件，性质同 admin-background 的 `schema.sql`。

### 初始化

log-sdk 只定义需要哪些参数，不负责读取参数：

```java
LogReporter.init(LogSdkConfig.builder()
        .appKey(appKey)        // 必填，项目 App Key
        .server(serverId)      // 必填，服务器标识
        .logDir(logDir)        // 必填，日志目录，与本机 Vector 读取的目录一致
        .keepHours(168)        // 选填，本地文件保留时长，默认 7 天
        .build());
```

- 启动时调用一次。重复调用参数相同时忽略，参数不同时抛异常，防止一个进程绑定两个项目。
- 初始化阶段快速失败：必填参数为空、目录不可写时直接抛异常，让启动失败。
- 使用阶段绝不报错：`send()` 不抛异常、不阻塞调用方；未初始化、队列已满或写盘失败时丢弃该条日志，同类警告只打印一次。
- `LogReporter.shutdown()` 和 JVM 关闭钩子：退出前把内存中尚未写入的日志写完。

### 使用

```java
LogReporter.event("player_login").actor(uid).put("ip", ip).put("level", 30).send();
LogReporter.event("pay_success").permanent().actor(uid).put("orderId", id).put("amount", 6.0).send();
```

### 写文件

写文件完全交给 Logback，SDK 不自己实现：

- 隔离：`init` 时在代码里创建一个独立的 Logback `LoggerContext`，不读取全局 `logback.xml`。业务方的日志配置影响不到 SDK，SDK 也不会写入业务日志。
- 切分与清理：`RollingFileAppender` + `TimeBasedRollingPolicy`，按小时切分（文件名形如 `event.2026-10-14-10.log`），按本地保留时长自动删除旧文件。
- 不阻塞：`AsyncAppender` 设置 `neverBlock=true`，队列满时直接丢弃，不阻塞业务线程；`shutdown()` 时停止上下文，把队列中的日志写完。
- 格式：输出格式只保留消息本身（`%msg%n`），每行就是 SDK 生成的 JSON。
- SDK 自己只负责参数校验、生成 `logId`、组装字段和 JSON 序列化（zfoo `JsonUtils`）。
- 依赖：Logback 使用与 zfoo 相同的版本，避免业务方出现两个版本。

## 五、日志格式（每行一条 JSON）

```json
{"logId":"01J9Z...","time":"2026-10-14T10:30:01.123+08:00","project":"<App Key>","server":"game-1","event":"pay_success","retention":"permanent","actor":"1001","data":{"orderId":"A20261014001","amount":6.0,"channel":"wechat"}}
```

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| logId | 是 | SDK 生成的唯一 ID（ULID），用于去重 |
| time | 是 | 事件发生时间，毫秒精度 |
| project | 是 | 项目 App Key，初始化时绑定 |
| server | 是 | 服务器标识，初始化时绑定 |
| event | 是 | 事件名，小写字母、数字和下划线 |
| retention | 是 | `normal` 或 `permanent`，调用 `.permanent()` 时为后者 |
| actor | 否 | 主体 ID（玩家 ID、账号 ID 等），单独成列，便于按人查 |
| data | 是 | 业务字段，任意 JSON 对象 |

## 六、ClickHouse

部署在 gz 服务器（111.230.143.165，4 核、3.6GB 内存、磁盘剩余约 64GB，已运行 PostgreSQL 和 Redis）：

- Docker 单节点，使用支持 `JSON` 类型的稳定版（25.3 及以上），数据目录挂载到宿主机。
- 内存有限，限制最多使用约 1.5GB，并调小缓存。
- 两个库 `logs_test`、`logs_prod`，表结构相同；每个库各有写入账号（给 Vector）和只读账号（给 admin 查询），以及一个用于修改保留期的管理账号。默认账号禁用，端口不对公网开放。

```sql
CREATE TABLE logs_prod.event
(
    log_id      String,
    time        DateTime64(3, 'Asia/Shanghai'),
    project     LowCardinality(String),
    server      LowCardinality(String),
    event       LowCardinality(String),
    retention   LowCardinality(String),
    actor       String,
    data        JSON,
    ingest_time DateTime64(3) DEFAULT now64(3)
)
ENGINE = ReplacingMergeTree(ingest_time)
PARTITION BY toYYYYMM(time)
ORDER BY (project, event, time, log_id)
TTL toDateTime(time) + INTERVAL 90 DAY DELETE WHERE retention = 'normal';
```

- `data` 使用 `JSON` 类型：每个字段路径自动存成独立的列，查询直接写 `data.amount`。
- 保留期：普通日志 90 天后自动删除，支付类永久保留；修改保留期执行 `ALTER TABLE ... MODIFY TTL`。
- 去重：Vector 是「至少一次」投递，`ReplacingMergeTree` 按 `log_id` 在后台合并去重；精确计数的查询（如支付对账）使用 `FINAL`。
- 容量：压缩后约 50～100MB/天，3 个月约 5～10GB；支付类按实际量永久增长。

## 七、Vector

每台业务机器一个：读取 log-sdk 的日志目录，校验必填字段并丢弃格式错误的行，按 1 秒或 1000 条批量写入对应环境的库，开启磁盘缓冲和确认机制，自动记录读取位置。

## 八、admin-background 数据中心模块

- **连接配置**：在「系统设置 → 日志存储」里配置 ClickHouse 地址、库名、只读账号和密码，提供「测试连接」；密码加密存储，接口和界面都不回显明文。内网后台和正式后台各自配置自己的库。数据中心页面显著显示当前连接的库名，避免查错环境。修改保留期使用单独的管理账号，同样在这里配置。
- **事件定义与查询模板**：存在 admin 的 PostgreSQL（配置数据）。事件定义包括事件名、展示名、字段列表（JSON 路径、类型、展示名），数据中心原型里的「查询模板 + 字段映射」保存到这里。
- **查询**：属于项目接口，走 `requireProjectMenu`，自动加上当前项目 App Key 的过滤，请求里不能指定其他项目；必须带时间范围；条件按字段类型生成参数化 SQL；分页，并限制最大返回条数和查询超时。
- **保留期**：在系统设置里修改普通日志的保留天数。
- **App Key 只读**：项目创建后 App Key 不可修改，前端编辑时只读，后端拒绝修改。
- **操作日志**：（已调整）admin 自己的操作日志存 PostgreSQL 表 `operation_log`，不走 log-sdk。日志存储配置只用于读取外部业务服务的日志。

## 九、实施顺序

1. 在 gz 上部署 ClickHouse：限制内存，建 `logs_test`、`logs_prod` 和账号，手写一条数据验证写入和查询。
2. 新建 `log-sdk` 仓库：SDK 代码和单元测试；本机装 Vector，跑通「SDK 写文件 → Vector → ClickHouse」。
3. admin-background：ClickHouse 连接配置、事件定义、查询模板、日志查询、保留期，App Key 改为只读。
4. admin-front：系统设置增加日志存储配置，数据中心接入真实接口。
5. （已调整）admin-background 的操作日志存 PostgreSQL，不接入 log-sdk。
