package com.oaixdog.adminbackground.service;

import com.oaixdog.adminbackground.entity.ServerHost;
import com.oaixdog.adminbackground.exception.BusinessException;
import com.oaixdog.adminbackground.exception.ErrorCode;
import com.zfoo.protocol.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务器监控数据：Redis 存心跳时间和最新指标（60 秒有效，30 秒没有心跳视为离线），供列表页使用；
 * 每次心跳的指标同时写入平台 ClickHouse 库的 server_metric 表（保留 7 天，process_id 为 0 的是主机），供监控曲线按时间范围聚合查询。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServerMetricService {

    private static final String TABLE = "server_metric";
    private static final Duration ONLINE_TTL = Duration.ofSeconds(30);
    private static final Duration LATEST_TTL = Duration.ofSeconds(60);
    private static final Duration LAST_SEEN_TTL = Duration.ofDays(30);
    private static final long WARN_INTERVAL = 60_000L;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.of("Asia/Shanghai"));
    /** 由 ClickHouse 攒批写入，心跳不等待写入完成 */
    private static final Map<String, String> INSERT_SETTINGS = Map.of("async_insert", "1", "wait_for_async_insert", "0");
    private static final Map<String, String> QUERY_SETTINGS = Map.of("readonly", "2", "max_execution_time", "15", "output_format_json_quote_64bit_integers", "0");
    private static final String QUERY = "SELECT toUnixTimestamp(toStartOfInterval(time, toIntervalSecond({step:UInt32}))) * 1000 AS bucket,"
            + " round(avg(cpu), 2) AS cpu, round(avg(load1), 2) AS load1, toUInt64(avg(memory_used)) AS memoryUsed, max(memory_total) AS memoryTotal,"
            + " toUInt64(avg(disk_used)) AS diskUsed, max(disk_total) AS diskTotal, toUInt64(avg(net_in)) AS netIn, toUInt64(avg(net_out)) AS netOut,"
            + " toUInt64(avg(packets_in)) AS packetsIn, toUInt64(avg(packets_out)) AS packetsOut, round(avg(tcp), 1) AS tcp, round(avg(threads), 1) AS threads"
            + " FROM " + TABLE + " WHERE server_id = {server:UInt64} AND process_id = {process:UInt64}"
            + " AND time >= fromUnixTimestamp64Milli({start:Int64}) AND time < fromUnixTimestamp64Milli({end:Int64}) GROUP BY bucket ORDER BY bucket";

    private final RedisService redisService;
    private final LogStorageService logStorageService;
    private final ClickHouseClient clickHouseClient;
    private volatile long lastWarnAt;

    /** 保存一次心跳：host 为主机指标，processes 为进程 ID → 进程指标 */
    public void saveHeartbeat(ServerHost server, Map<String, Object> host, Map<Long, Map<String, Object>> processes) {
        long now = System.currentTimeMillis();
        redisService.set(seenKey(server.getId()), String.valueOf(now), ONLINE_TTL);
        redisService.set("server:last-seen:" + server.getId(), String.valueOf(now), LAST_SEEN_TTL);
        saveLatest("host", server.getId(), host, now);
        processes.forEach((processId, metric) -> saveLatest("process", processId, metric, now));
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row(server, 0L, host, now));
        processes.forEach((processId, metric) -> rows.add(row(server, processId, metric, now)));
        ClickHouseClient.Connection connection = logStorageService.adminConnection();
        try {
            if (connection == null) {
                throw new ClickHouseClient.ClickHouseException("未配置 ClickHouse 管理账号");
            }
            clickHouseClient.insert(connection, TABLE, rows, INSERT_SETTINGS);
        } catch (ClickHouseClient.ClickHouseException exception) {
            // 写不进去不影响心跳，同类告警每分钟最多打印一次
            if (now - lastWarnAt > WARN_INTERVAL) {
                lastWarnAt = now;
                log.warn("服务器监控数据写入 ClickHouse 失败: {}", exception.getMessage());
            }
        }
    }

    public boolean isOnline(long serverId) {
        return redisService.hasKey(seenKey(serverId));
    }

    /** 最后一次心跳的时间（毫秒），30 天内没有心跳为 null */
    public Long lastSeen(long serverId) {
        String value = redisService.get("server:last-seen:" + serverId);
        return value == null ? null : Long.valueOf(value);
    }

    public Map<String, Object> hostMetric(long serverId) {
        String value = redisService.get(latestKey("host", serverId));
        return value == null ? null : toMap(value);
    }

    /** 进程 ID → 最新指标，没有的不放入 */
    public Map<Long, Map<String, Object>> processMetrics(List<Long> processIds) {
        Map<Long, Map<String, Object>> result = new HashMap<>();
        if (processIds.isEmpty()) {
            return result;
        }
        List<String> values = redisService.multiGet(processIds.stream().map(id -> latestKey("process", id)).toList());
        for (int index = 0; index < processIds.size() && index < values.size(); index++) {
            if (values.get(index) != null) {
                result.put(processIds.get(index), toMap(values.get(index)));
            }
        }
        return result;
    }

    /** 按 step 秒聚合的曲线，每个点取该时间段的平均值；processId 为 0 时查主机 */
    public List<Map<String, Object>> query(long serverId, long processId, long startTime, long endTime, int step) {
        ClickHouseClient.Connection connection = logStorageService.adminConnection();
        if (connection == null) {
            throw new BusinessException(ErrorCode.SERVER_METRIC_UNAVAILABLE);
        }
        Map<String, String> params = Map.of("server", String.valueOf(serverId), "process", String.valueOf(processId), "start", String.valueOf(startTime),
                "end", String.valueOf(endTime), "step", String.valueOf(step));
        try {
            // 聚合列不能叫 time（会在 WHERE 里替换掉同名列），查询后再改名
            List<Map<String, Object>> rows = clickHouseClient.query(connection, QUERY, params, QUERY_SETTINGS);
            rows.forEach(row -> row.put("time", row.remove("bucket")));
            return rows;
        } catch (ClickHouseClient.ClickHouseException exception) {
            log.warn("查询服务器监控数据失败: {}", exception.getMessage());
            throw new BusinessException(ErrorCode.LOG_STORAGE_UNAVAILABLE);
        }
    }

    private void saveLatest(String kind, long id, Map<String, Object> metric, long now) {
        Map<String, Object> value = new HashMap<>(metric);
        value.put("time", now);
        redisService.set(latestKey(kind, id), JsonUtils.object2String(value), LATEST_TTL);
    }

    private static Map<String, Object> row(ServerHost server, long processId, Map<String, Object> metric, long now) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("time", TIME.format(Instant.ofEpochMilli(now)));
        row.put("project_id", server.getProjectId());
        row.put("server_id", server.getId());
        row.put("process_id", processId);
        row.put("cpu", number(metric.get("cpu")));
        row.put("load1", number(metric.get("load1")));
        row.put("memory_used", number(processId == 0 ? metric.get("memoryUsed") : metric.get("memory")));
        row.put("memory_total", number(metric.get("memoryTotal")));
        row.put("disk_used", number(metric.get("diskUsed")));
        row.put("disk_total", number(metric.get("diskTotal")));
        row.put("net_in", number(metric.get("netIn")));
        row.put("net_out", number(metric.get("netOut")));
        row.put("packets_in", number(metric.get("packetsIn")));
        row.put("packets_out", number(metric.get("packetsOut")));
        row.put("tcp", number(metric.get("tcp")));
        row.put("threads", number(metric.get("threads")));
        return row;
    }

    private static Number number(Object value) {
        return value instanceof Number number && number.doubleValue() >= 0 ? number : 0;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toMap(String value) {
        return JsonUtils.string2Object(value, Map.class);
    }

    private static String seenKey(long serverId) {
        return "server:seen:" + serverId;
    }

    private static String latestKey(String kind, long id) {
        return "server:metric:" + kind + ":" + id;
    }
}
