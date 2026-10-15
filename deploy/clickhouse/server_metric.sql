-- 服务器监控数据：Agent 每 5 秒一次心跳写入一行主机指标（process_id = 0）和每个托管进程一行，保留 7 天。
-- 建在平台日志存储库里（系统设置 → 日志保留期 里配置的库，例如 logs_test / logs_prod），用同一个管理账号执行：
--   clickhouse-client --user logs_admin --database logs_test --multiquery < server_metric.sql
CREATE TABLE IF NOT EXISTS server_metric
(
    time         DateTime64(3, 'Asia/Shanghai'),
    project_id   UInt64,
    server_id    UInt64,
    process_id   UInt64,
    cpu          Float32,
    load1        Float32,
    memory_used  UInt64,
    memory_total UInt64,
    disk_used    UInt64,
    disk_total   UInt64,
    net_in       UInt64,
    net_out      UInt64,
    packets_in   UInt64,
    packets_out  UInt64,
    tcp          UInt32,
    threads      UInt32
)
ENGINE = MergeTree
PARTITION BY toYYYYMMDD(time)
ORDER BY (server_id, process_id, time)
TTL toDateTime(time) + INTERVAL 7 DAY;
