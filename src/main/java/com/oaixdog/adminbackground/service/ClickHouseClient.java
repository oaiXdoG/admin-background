package com.oaixdog.adminbackground.service;

import com.zfoo.protocol.util.JsonUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 通过 ClickHouse 官方 HTTP 接口执行 SQL。条件值一律走查询参数（SQL 里写 {name:Type}，请求带 param_name），不拼进 SQL。
 * HttpClient 默认不走系统代理，不受本机 Clash 影响。
 */
@Service
public class ClickHouseClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final int ERROR_MAX_LENGTH = 300;

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();

    public record Connection(String url, String database, String user, String password) {
    }

    /** 执行查询，返回结果行；settings 为 ClickHouse 查询级设置，例如 max_execution_time */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> query(Connection connection, String sql, Map<String, String> params, Map<String, String> settings) {
        String body = send(connection, sql + " FORMAT JSON", params, settings);
        Map<String, Object> result = JsonUtils.string2Object(body, Map.class);
        return (List<Map<String, Object>>) result.getOrDefault("data", List.of());
    }

    /** 按 JSONEachRow 写入多行；settings 例如 async_insert（由 ClickHouse 攒批写入） */
    public void insert(Connection connection, String table, List<Map<String, Object>> rows, Map<String, String> settings) {
        String body = rows.stream().map(JsonUtils::object2String).collect(Collectors.joining("\n"));
        send(connection, "INSERT INTO " + table + " FORMAT JSONEachRow\n" + body, Map.of(), settings);
    }

    /** 执行不返回结果的语句，例如 ALTER TABLE */
    public void execute(Connection connection, String sql) {
        send(connection, sql, Map.of(), Map.of());
    }

    private String send(Connection connection, String sql, Map<String, String> params, Map<String, String> settings) {
        Map<String, String> query = new LinkedHashMap<>(settings);
        query.put("database", connection.database());
        params.forEach((name, value) -> query.put("param_" + name, value));
        String queryString = query.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        HttpRequest request = HttpRequest.newBuilder(URI.create(connection.url().replaceAll("/+$", "") + "/?" + queryString))
                .timeout(REQUEST_TIMEOUT)
                .header("X-ClickHouse-User", connection.user())
                .header("X-ClickHouse-Key", connection.password())
                .POST(HttpRequest.BodyPublishers.ofString(sql, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new ClickHouseException(abbreviate(response.body()));
            }
            return response.body();
        } catch (IOException exception) {
            throw new ClickHouseException("无法连接：" + exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ClickHouseException("请求被中断");
        }
    }

    private static String abbreviate(String text) {
        String value = text == null ? "" : text.trim();
        return value.length() > ERROR_MAX_LENGTH ? value.substring(0, ERROR_MAX_LENGTH) + "…" : value;
    }

    /** 网络失败或 ClickHouse 返回错误，message 是可以展示给管理员的原因 */
    public static class ClickHouseException extends RuntimeException {
        public ClickHouseException(String message) {
            super(message);
        }
    }
}
