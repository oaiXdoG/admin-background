# 更新记录

## v0.1.0（2026-10-15）

系统管理、项目设置、日志中心三个模块的接口开发完成，已部署到 gz（systemd `admin-background`，前端经 nginx `/api` 转发），配合 admin-front `v0.1.0` 在浏览器中逐页走查。

### 系统管理

- 项目、用户、平台角色、角色模板、菜单的增删改和菜单分配；按钮级权限后端校验（`PermissionService`）。
- 系统设置：品牌信息、日志保留期（ClickHouse TTL）、数据备份（`pg_dump` 定时 / 手动，保留份数，下载）。
- 操作日志：登录、退出和所有写操作写入 PostgreSQL 表 `operation_log`，不使用 log-sdk；密码、密钥、图片脱敏。
- 安全：BCrypt 密码、密码规则、登录失败锁定（账号 5 次 / IP 20 次，15 分钟）、会话 12 小时滑动过期。

### 项目设置

- 成员管理、项目角色（含从模板导入）；新项目自动创建「项目管理员」角色。

### 日志中心

- 日志源：每个项目一个 ClickHouse 只读连接（`log_source`，密码 AES-GCM 加密，地址做 SSRF 检查）。
- 事件字典（`log_event`）：事件和字段的中文名、展示类型，只影响展示。
- 日志查询 / 导出：强制本项目 App Key 和时间范围；事件、用户、服务器、关键字、字段路径条件（且 / 或，可跨多个事件）；全部参数化。字段缺失或为 null 时不命中，数字字段接受数字文本，不带时区的日期文本按 Asia/Shanghai 解析。
- 事件发现：最近上报的事件、事件的字段路径和类型（`distinctJSONPathsAndTypes`）。

### 数据中心（接口已完成，待业务服务器接入）

- 数据源（业务服务器接口地址 + HMAC-SHA256 签名密钥）、查询模板、按参数查询和导出。接口规范见 `docs/data-center.md`。

### 文档

- `docs/log-center.md`、`docs/data-center.md`、`docs/permission-flow.md`、`docs/backup.md`、`deploy/README.md`。

### 未包含

- 首页运营看板及日志统计。
- 服务器管理（性能监控、运维操作）后端。
