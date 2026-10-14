# 部署（gz）

后端部署在 gz（`111.230.143.165`），与 PostgreSQL、Redis、ClickHouse 同机。后台操作日志存在 PostgreSQL 表 `operation_log`，不经过 log-sdk 和 Vector。

| 项 | 位置 |
| --- | --- |
| 程序 | `/opt/admin-background/lib`（主 jar + 依赖） |
| 配置 | `/opt/admin-background/conf`，`application.properties` 只在服务器上维护，发布不会覆盖 |
| 服务 | systemd `admin-background`，运行账号 `admin-background`，端口 `9050`，堆内存 512MB |
| 程序日志 | journald：`journalctl -u admin-background -f` |
| 数据备份 | `/data/admin-background/backup`（`backup.dir`），在「系统管理 → 系统设置 → 数据备份」配置定时、保留份数，可立即备份、下载、删除 |

## 发布

本机执行（需要 JDK 21）：

```bash
deploy/deploy.sh
```

编译打包、上传 jar 和 `config.xml`、`protocol.xml`、`logback.xml`，然后重启服务。

## 首次安装

1. 服务器上准备 `/opt/admin-background/conf/application.properties`：以 `conf/application.properties` 为基础，数据库和 Redis 地址改为 `127.0.0.1`，加上 `backup.dir=/data/admin-background/backup`。
2. 执行一次 `deploy/deploy.sh` 上传程序（重启会失败，忽略），再在服务器上执行 `bash /opt/admin-background/deploy/setup.sh && systemctl start admin-background`。

## 从备份恢复

备份是 `pg_dump -Fc` 的自定义格式。恢复会覆盖库里的数据，先停服务：

```bash
ssh gz 'systemctl stop admin-background'
ssh gz 'PGPASSWORD=<密码> pg_restore -h 127.0.0.1 -p 28417 -U <用户> -d admin_background --clean --if-exists /data/admin-background/backup/<文件名>.dump'
ssh gz 'systemctl start admin-background'
```

## 常用命令

```bash
ssh gz 'systemctl status admin-background'
ssh gz 'journalctl -u admin-background -n 200 --no-pager'
```
