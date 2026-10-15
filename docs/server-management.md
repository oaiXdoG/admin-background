# 服务器管理（第一期）

每台业务服务器运行一个 Agent（`agent/` 目录，Java 17+ 单个 jar，说明见 `agent/README.md`），由它在本机托管业务进程（systemd）、执行指令和上报监控。后台不保存任何 SSH 账号、密码或密钥。

```
后台 admin-background ◀── Agent 每 5 秒调用 /api/agent/heartbeat：上报指标，领取待执行指令
                       ◀── Agent 执行完调用 /api/agent/result：回传结果和输出
业务服务器：Agent（systemd 服务 admin-agent）
   └─ 每个业务进程一个 systemd 服务 adm-<进程名>.service，ExecStart 就是 java ... -jar ...
```

第一期范围：服务器接入、进程登记（托管模式，含 JVM 参数模板）、启动 / 停止 / 重启、监控（当前值 + 最近 24 小时每分钟趋势）、执行记录。版本更新、改配置、批量滚动、热更新在后续阶段。

## 一、接入流程

1. 后台「服务器管理 → 服务器 → 新增」：填名称、分组，保存后得到一条安装命令（令牌 10 分钟内有效，只能用一次）。
2. 运维登录这台服务器执行安装命令（需要 root 和 Java 17+）：

   ```bash
   curl -fsSL http://111.230.143.165/agent/install.sh | bash -s -- http://111.230.143.165 <令牌>
   ```

   脚本把 Agent 装到 `/opt/admin-agent`，注册成 systemd 服务 `admin-agent`（开机自启）。
3. Agent 启动后用令牌调用 `/api/agent/register`，换取服务器 ID 和专属密钥，写回本机 `/opt/admin-agent/agent.properties`（只有 root 可读），之后只用密钥签名通信。
4. 对已接入的服务器再次生成安装命令并执行，会重新注册并更换密钥（旧密钥立即失效），用于重装或密钥泄露时重置。

安装命令里的地址来自 `conf/application.properties` 的 `agent.base-url`，Agent 和安装脚本由 nginx 在 `/agent/` 下提供（见 admin-front `deploy/nginx.conf`）。

## 二、进程（托管模式）

| 字段 | 说明 |
| --- | --- |
| name | 进程名，同一服务器内唯一，创建后不能改；小写字母开头，小写字母、数字和 `-`，最长 48；systemd 服务名为 `adm-<name>` |
| workDir | 工作目录，绝对路径 |
| javaPath | java 可执行文件，默认 `java`（按 PATH 查找） |
| templateId | JVM 参数模板，可空 |
| jvmOptions | 本进程的 JVM 参数，追加在模板参数之后（同名参数后者生效） |
| jar | 启动 jar（`-jar` 方式），相对工作目录或绝对路径 |
| classpath / mainClass | `-cp` 方式：填了主类时用 `-cp <classpath> <主类>` 启动、不使用 jar，例如 `conf:lib/*:loginServer.jar` 和 `com.oaixdog.loginserver.Application`；`lib/*` 原样交给 java 展开 |
| programArgs | 程序参数，可空 |
| env | 环境变量，每行 `KEY=VALUE`，可空 |
| runUser | 运行用户，空为 root（建议用普通用户） |
| port | 启动后检查的端口，0 表示不检查 |

生成的 systemd 服务：

```ini
[Unit]
Description=<服务器名> <进程名>（admin-agent 托管）
After=network.target

[Service]
Type=simple
User=<runUser>
WorkingDirectory=<workDir>
Environment="KEY=VALUE"
ExecStart=<javaPath> <模板参数> <jvmOptions> -jar <jar> <programArgs>
# 或 -cp 方式：ExecStart=<javaPath> <模板参数> <jvmOptions> -cp <classpath> <mainClass> <programArgs>
Restart=on-failure
RestartSec=5
TimeoutStopSec=60
SuccessExitStatus=143

[Install]
WantedBy=multi-user.target
```

- 进程定义修改后 `configVersion` 加 1，Agent 在下一次心跳时更新服务文件（`systemctl daemon-reload`），**重启后生效**；运行中且启动时的版本低于当前版本的进程显示「待重启」。
- 启动 / 重启：`systemctl enable` + `start/restart`，然后最多等 180 秒确认服务处于运行状态（配置了端口时同时确认端口可连），失败时附上 `systemctl status` 和最近 30 行 journal 日志。
- 停止：`systemctl stop`，先发 SIGTERM，60 秒未退出再 SIGKILL。
- 删除进程：只能删除已停止的进程；后台下发 `remove` 指令，Agent 禁用并删除服务文件，不删除工作目录。

## 三、监控

Agent 每 5 秒随心跳上报：

- 服务器信息：主机名、系统、CPU 核数、内存总量、内网 IP（本机网卡的 IPv4，跳过回环和 Docker、bridge、veth 等虚拟网卡，多个用逗号分隔）；公网 IP 为后台看到的访问来源（经 nginx 时取 X-Real-IP），多台机器共用出口时相同。
- 主机：CPU 使用率、1 分钟负载、内存已用 / 总量、根分区已用 / 总量、网络收发速率、每秒收发包数、已建立的 TCP 连接数、开机时长。
- 进程：systemd 状态、PID、CPU 使用率（占整机）、常驻内存、线程数、已建立的 TCP 连接数、启动时间、启动时的配置版本。

存储：

- Redis：心跳时间和最新指标，保留 60 秒，供列表页使用；超过 30 秒没有心跳显示离线。
- ClickHouse：每次心跳写入平台日志存储库（「系统设置 → 日志保留期」配置的库和管理账号）的 `server_metric` 表，主机一行（`process_id = 0`）、每个托管进程一行，保留 7 天。建表语句 `deploy/clickhouse/server_metric.sql`。使用 `async_insert` 由 ClickHouse 攒批写入，写入失败不影响心跳（告警每分钟最多打印一次）。

页面「监控详情」（隐藏菜单 `server_monitor`，从服务器列表 / 进程管理的「监控」进入）：选择服务器和对象（主机或某个进程），时间范围为实时（最近 1 小时，每 10 秒刷新）、近 24 小时、近 7 天或选择日期（7 天内），粒度 10 秒 / 1 分钟 / 5 分钟 / 1 小时（每条曲线最多约 2000 个点）。每个指标一行：曲线、Max / Min / Avg（当前曲线所有点）、放大查看；所有曲线的提示框联动；可导出 CSV。

## 四、执行记录

表 `server_task`。操作人每发起一次启动 / 停止 / 重启 / 删除，按进程生成一条记录：等待 → 执行中（Agent 领取）→ 成功 / 失败，保存执行输出。等待超过 10 分钟或执行超过 5 分钟没有结果显示为「超时」。同一进程有未完成的指令时不能再发起新指令。服务器离线时不能发起指令。

## 五、接口

### 后台页面接口

权限：查看需要项目菜单 `server_host`（服务器）或 `server_process`（进程）；操作按钮见下。

| 接口 | 权限 | 请求 → 返回 |
| --- | --- | --- |
| `POST /api/server/host/list` | `server_host` 或 `server_process` | `{ projectId }` → `{ list }`，每项含基本信息、`status`（`pending` 未接入 / `online` / `offline`）、`lastSeenAt`、`metric`（最新主机指标，离线为 null） |
| `POST /api/server/host/create` | `server_host:create` | `{ projectId, name, groupName, remark }` → `{ server, installCommand }` |
| `POST /api/server/host/update` | `server_host:update` | `{ projectId, serverId, name, groupName, remark }` → 服务器 |
| `POST /api/server/host/remove` | `server_host:remove` | `{ projectId, serverId }`；服务器下还有进程时不能删除 |
| `POST /api/server/host/install` | `server_host:update` | `{ projectId, serverId }` → `{ installCommand }`，生成新的一次性令牌 |
| `POST /api/server/process/list` | `server_process` | `{ projectId }` → `{ list }`，每项含定义、`state`（`active` / `inactive` / `failed` / `activating` / `deactivating` / `unknown`，服务器离线时为 `unknown`）、`pendingRestart`、`metric`、`task`（未完成的指令） |
| `POST /api/server/process/create` | `server_process:create` | 进程字段 + `{ projectId, serverId }` → 进程 |
| `POST /api/server/process/update` | `server_process:update` | 进程字段（不含 name、serverId）+ `{ projectId, processId }` → 进程 |
| `POST /api/server/process/remove` | `server_process:remove` | `{ projectId, processId }` |
| `POST /api/server/process/operate` | `server_process:operate` | `{ projectId, processIds, action }`，action 为 `start` / `stop` / `restart`，一次最多 50 个 → `{ taskIds }` |
| `POST /api/server/metric/query` | 项目 `server_monitor` | `{ projectId, serverId, processId, startTime, endTime, step }`（processId 为空查主机，step 为 10 / 60 / 300 / 3600 秒，范围不超过 7 天、每条曲线不超过 2100 个点）→ `{ list }`，每项 `{ time, cpu, load1, memoryUsed, memoryTotal, diskUsed, diskTotal, netIn, netOut, packetsIn, packetsOut, tcp, threads }`，为该时间段的平均值 |
| `POST /api/server/task/list` | `server_process` | `{ projectId, processId, page, pageSize }`（processId 可空）→ `{ total, list }`，按时间倒序 |
| `POST /api/server/template/list` | `server_process` | `{ projectId }` → `{ list }` |
| `POST /api/server/template/save` | `server_process:update` | `{ projectId, templateId, name, options }`，templateId 为空表示新建 → 模板 |
| `POST /api/server/template/remove` | `server_process:update` | `{ projectId, templateId }`；有进程在用时不能删除 |

错误码：`100065` 服务器不存在、`100066` 服务器名称已存在、`100067` 服务器下还有进程、`100068` 服务器离线、`100069` 进程不存在、`100070` 进程名已存在、`100071` 进程配置不合法、`100072` 进程有未完成的指令、`100073` 进程未停止、`100074` JVM 模板不存在、`100075` JVM 模板名称已存在、`100076` JVM 模板正在使用、`100077` 安装令牌无效、`100078` Agent 签名无效、`100079` 监控数据不可用（未配置 ClickHouse 管理账号）。

### Agent 接口

不需要登录。除注册外都要签名：请求头 `X-Agent-Server`（服务器 ID）、`X-Agent-Timestamp`（毫秒，与服务器时间相差不超过 5 分钟）、`X-Agent-Signature`（`HMAC-SHA256(密钥, 时间戳 + "\n" + SHA-256(请求体))` 的十六进制，SHA-256 也用十六进制小写）。

| 接口 | 请求 → 返回 |
| --- | --- |
| `POST /api/agent/register` | `{ token, hostname, os, privateIp, cpuCores, memoryTotal, agentVersion }` → `{ serverId, secret }` |
| `POST /api/agent/heartbeat` | `{ hostname, os, privateIp, cpuCores, memoryTotal, agentVersion, host, processes }` → `{ processes, tasks }`：`processes` 为本服务器全部进程定义（含合并后的 JVM 参数和 `configVersion`），`tasks` 为本次领取的指令 `{ taskId, processId, processName, action }` |
| `POST /api/agent/result` | `{ taskId, success, output }` → `null` |

`host`：`{ cpu, load1, memoryUsed, memoryTotal, diskUsed, diskTotal, netIn, netOut, packetsIn, packetsOut, tcp, uptime }`（百分比 0～100，字节，字节/秒，个/秒，个，秒）。`processes[]`：`{ processId, state, pid, cpu, memory, threads, tcp, startedAt, configVersion }`。

## 六、菜单

服务器管理（`server`）下：服务器列表 `server_host`（按钮 `:create`、`:update`、`:remove`）、进程管理 `server_process`（按钮 `:create`、`:update`、`:remove`、`:operate`）、监控详情 `server_monitor`（隐藏菜单，不在侧边栏显示）。原来的性能监控、运维操作两个演示页面删除。
