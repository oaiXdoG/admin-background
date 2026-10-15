package com.oaixdog.adminbackground.service;

import com.oaixdog.adminbackground.entity.Project;
import com.oaixdog.adminbackground.exception.BusinessException;
import com.oaixdog.adminbackground.exception.ErrorCode;
import com.oaixdog.adminbackground.mapper.ProjectMapper;
import com.oaixdog.adminbackground.protocol.LogQueryCondition;
import com.oaixdog.adminbackground.protocol.LogQueryResponse;
import com.zfoo.protocol.util.JsonUtils;
import com.zfoo.protocol.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * 日志中心查询 ClickHouse 日志表（项目选择的日志源）：
 * - 日志查询（log_query）：按时间、事件（可多选）、玩家、服务器、关键字过滤，也可以按字段路径加条件；
 * - 事件列表和字段发现：日志查询和事件管理（log_event）页面用来列出事件、读取字段路径、类型和样例。
 * 项目 App Key 由服务器按本次校验通过的项目填入，时间范围必填；字段路径和条件值全部走查询参数，SQL 里只拼固定的表达式。
 */
@Service
@RequiredArgsConstructor
public class LogQueryService {

    private static final Logger log = LoggerFactory.getLogger(LogQueryService.class);

    private static final String LOG_MENU = "log_query";
    private static final String EVENT_MENU = "log_event";
    private static final int PAGE_SIZE_MAX = 200;
    private static final int OFFSET_MAX = 10_000;
    private static final int EXPORT_MAX = 50_000;
    private static final int CONDITIONS_MAX = 20;
    private static final int EVENTS_MAX = 50;
    private static final int TEXT_MAX_LENGTH = 128;
    private static final int DISCOVER_DAYS_MAX = 90;
    /** 不带时区的日期文本按这个时区解析，与日志表 time 列的时区一致 */
    private static final String TIME_ZONE = "Asia/Shanghai";
    private static final int DISCOVER_SAMPLE_ROWS = 1000;
    private static final Map<String, String> SETTINGS = Map.of("max_execution_time", "15", "readonly", "2");
    private static final Map<String, String> EXPORT_SETTINGS = Map.of("max_execution_time", "60", "readonly", "2");
    private static final Set<String> COMPARE = Set.of("eq", "ne", "gt", "ge", "lt", "le", "between");
    private static final Set<String> TEXT_COMPARE = Set.of("eq", "ne", "contains");
    private static final Set<String> BOOLEAN_COMPARE = Set.of("eq", "ne");
    private static final Set<String> TYPES = Set.of("string", "number", "boolean", "time", "json", "jsonText");
    private static final Map<String, String> OPERATORS = Map.of("eq", "=", "ne", "!=", "gt", ">", "ge", ">=", "lt", "<", "le", "<=");
    private static final Map<String, String> COLUMNS = Map.of("server", "server", "actor", "actor", "event", "event", "logId", "log_id", "retention", "retention");
    private static final Pattern DATA_PATH = Pattern.compile("^data(\\.[A-Za-z0-9_$-]{1,64}){1,8}$");
    private static final String SELECT = "SELECT log_id, toUnixTimestamp64Milli(time) AS time_ms, server, actor, event, retention, toJSONString(data) AS data_json FROM ";
    private static final String BASE_WHERE = "project = {project:String}"
            + " AND time >= fromUnixTimestamp64Milli({start:Int64}) AND time < fromUnixTimestamp64Milli({end:Int64})";

    private final PermissionService permissionService;
    private final LogSourceService logSourceService;
    private final ClickHouseClient clickHouseClient;
    private final ProjectMapper projectMapper;

    /** 条件的取值来源和类型：日志自带列或 data.<路径> */
    private record Field(String source, String type) {
    }

    /** 一次查询的条件：where 只含固定表达式和参数占位符，params 是对应的参数值 */
    private record Filter(ClickHouseClient.Connection connection, String table, String where, Map<String, String> params) {
    }

    /** 查询条件（日志查询和导出共用） */
    public record LogFilter(Long startTime, Long endTime, List<String> events, String actor, String server, String keyword,
                            String relation, List<LogQueryCondition> conditions) {
    }

    public LogQueryResponse queryLogs(String authorization, Long projectId, LogFilter request, Integer page, Integer pageSize) {
        PermissionService.ProjectAccess access = permissionService.requireProjectMenu(authorization, projectId, LOG_MENU);
        return page(filter(access, request), page, pageSize);
    }

    /** 导出：最多 5 万行，需要按钮权限 log_query:export */
    public LogQueryResponse exportLogs(String authorization, Long projectId, LogFilter request) {
        permissionService.requireProjectMenu(authorization, projectId, LOG_MENU + ":export");
        PermissionService.ProjectAccess access = permissionService.requireProjectMenu(authorization, projectId, LOG_MENU);
        return all(filter(access, request));
    }

    /** 日志源里最近 days 天有数据的事件，按条数倒序 */
    public List<Map<String, Object>> listEvents(String authorization, Long projectId, Integer days) {
        PermissionService.ProjectAccess access = permissionService.requireAnyProjectMenu(authorization, projectId, LOG_MENU, EVENT_MENU);
        int range = discoverDays(days);
        ClickHouseClient.Connection connection = logSourceService.requireConnection(access.projectId());
        Map<String, String> params = Map.of("project", appKey(access), "days", String.valueOf(range));
        List<Map<String, Object>> rows = run(() -> clickHouseClient.query(connection,
                "SELECT event, count() AS total, toUnixTimestamp64Milli(max(time)) AS last_time FROM " + table(connection)
                        + " WHERE project = {project:String} AND time >= now() - toIntervalDay({days:UInt32}) GROUP BY event ORDER BY total DESC LIMIT 500",
                params, SETTINGS));
        return rows.stream().map(row -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("event", row.get("event"));
            result.put("total", Long.parseLong(String.valueOf(row.get("total"))));
            result.put("lastTime", Long.parseLong(String.valueOf(row.get("last_time"))));
            return result;
        }).toList();
    }

    /**
     * 事件的字段：最近 days 天内最多 1000 条日志里出现过的 data 字段路径和 ClickHouse 类型，以及最新一条日志的 data 作为样例。
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> eventFields(String authorization, Long projectId, String event, Integer days) {
        PermissionService.ProjectAccess access = permissionService.requireAnyProjectMenu(authorization, projectId, LOG_MENU, EVENT_MENU);
        if (event == null || !LogEventService.EVENT.matcher(event).matches()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        int range = discoverDays(days);
        ClickHouseClient.Connection connection = logSourceService.requireConnection(access.projectId());
        Map<String, String> params = Map.of("project", appKey(access), "event", event, "days", String.valueOf(range),
                "limit", String.valueOf(DISCOVER_SAMPLE_ROWS));
        String recent = "SELECT data, time FROM " + table(connection) + " WHERE project = {project:String} AND event = {event:String}"
                + " AND time >= now() - toIntervalDay({days:UInt32}) ORDER BY time DESC LIMIT {limit:UInt32}";
        List<Map<String, Object>> paths = run(() -> clickHouseClient.query(connection,
                "SELECT distinctJSONPathsAndTypes(data) AS paths FROM (" + recent + ")", params, SETTINGS));
        List<Map<String, Object>> sample = run(() -> clickHouseClient.query(connection,
                "SELECT toJSONString(data) AS data_json FROM (" + recent + ") LIMIT 1", params, SETTINGS));
        Map<String, Object> pathTypes = paths.isEmpty() ? Map.of() : (Map<String, Object>) paths.getFirst().getOrDefault("paths", Map.of());
        List<Map<String, Object>> fields = new ArrayList<>();
        pathTypes.forEach((path, types) -> fields.add(Map.of("path", path, "types", types)));
        fields.sort((a, b) -> String.valueOf(a.get("path")).compareTo(String.valueOf(b.get("path"))));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fields", fields);
        result.put("sample", sample.isEmpty() ? null : JsonUtils.string2Object(String.valueOf(sample.getFirst().get("data_json")), Map.class));
        return result;
    }

    private Filter filter(PermissionService.ProjectAccess access, LogFilter request) {
        if (request.startTime() == null || request.endTime() == null || request.startTime() >= request.endTime()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        List<String> events = request.events() == null ? List.of() : request.events();
        if (events.size() > EVENTS_MAX || events.stream().anyMatch(item -> item == null || !LogEventService.EVENT.matcher(item).matches())
                || tooLong(request.actor()) || tooLong(request.server()) || tooLong(request.keyword())) {
            throw new BusinessException(ErrorCode.QUERY_CONDITION_INVALID);
        }
        ClickHouseClient.Connection connection = logSourceService.requireConnection(access.projectId());
        Map<String, String> params = new HashMap<>();
        params.put("project", appKey(access));
        params.put("start", String.valueOf(request.startTime()));
        params.put("end", String.valueOf(request.endTime()));
        StringBuilder where = new StringBuilder(BASE_WHERE);
        if (!events.isEmpty()) {
            // 事件数组参数：ClickHouse 数组字面量，事件名已校验为小写字母、数字和下划线
            params.put("events", "['" + String.join("','", events) + "']");
            where.append(" AND event IN {events:Array(String)}");
        }
        if (StringUtils.isNotBlank(request.actor())) {
            params.put("actor", request.actor().trim());
            where.append(" AND actor = {actor:String}");
        }
        if (StringUtils.isNotBlank(request.server())) {
            params.put("server", request.server().trim());
            where.append(" AND server = {server:String}");
        }
        if (StringUtils.isNotBlank(request.keyword())) {
            params.put("keyword", request.keyword().trim());
            where.append(" AND positionCaseInsensitiveUTF8(toJSONString(data), {keyword:String}) > 0");
        }
        where.append(conditionSql(request.relation(), request.conditions(), params));
        return new Filter(connection, table(connection), where.toString(), params);
    }

    /** 条件字段：自带列（time 只能是时间，其余文本或数字）或 data.<路径> */
    private static Field field(String path, String type) {
        String source = path == null ? "" : path.trim();
        boolean valid = TYPES.contains(type) && ("time".equals(source) ? "time".equals(type)
                : COLUMNS.containsKey(source) ? "string".equals(type) || "number".equals(type) : DATA_PATH.matcher(source).matches());
        if (!valid) {
            throw new BusinessException(ErrorCode.QUERY_CONDITION_INVALID);
        }
        return new Field(source, type);
    }

    private LogQueryResponse page(Filter filter, Integer page, Integer pageSize) {
        int pageNumber = page == null ? 1 : page;
        int size = pageSize == null ? 20 : pageSize;
        if (pageNumber < 1 || size < 1 || size > PAGE_SIZE_MAX || (long) (pageNumber - 1) * size > OFFSET_MAX) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        List<Map<String, Object>> count = run(() -> clickHouseClient.query(filter.connection(),
                "SELECT count() AS total FROM " + filter.table() + " WHERE " + filter.where(), filter.params(), SETTINGS));
        Map<String, String> params = new HashMap<>(filter.params());
        params.put("limit", String.valueOf(size));
        params.put("offset", String.valueOf((pageNumber - 1) * size));
        List<Map<String, Object>> rows = run(() -> clickHouseClient.query(filter.connection(), SELECT + filter.table() + " WHERE " + filter.where()
                + " ORDER BY time DESC LIMIT {limit:UInt32} OFFSET {offset:UInt32}", params, SETTINGS));
        return response(filter, count.isEmpty() ? 0 : Long.parseLong(String.valueOf(count.getFirst().get("total"))), rows);
    }

    private LogQueryResponse all(Filter filter) {
        Map<String, String> params = new HashMap<>(filter.params());
        params.put("limit", String.valueOf(EXPORT_MAX));
        List<Map<String, Object>> rows = run(() -> clickHouseClient.query(filter.connection(), SELECT + filter.table() + " WHERE " + filter.where()
                + " ORDER BY time DESC LIMIT {limit:UInt32}", params, EXPORT_SETTINGS));
        return response(filter, rows.size(), rows);
    }

    private static LogQueryResponse response(Filter filter, long total, List<Map<String, Object>> rows) {
        LogQueryResponse response = new LogQueryResponse();
        response.setDatabase(filter.connection().database());
        response.setTotal(total);
        response.setList(rows.stream().map(LogQueryService::toRow).toList());
        return response;
    }

    /** ClickHouse 失败转为业务错误并附带原因，原因同时写日志 */
    private static <T> T run(Supplier<T> action) {
        try {
            return action.get();
        } catch (ClickHouseClient.ClickHouseException exception) {
            log.warn("日志查询失败: {}", exception.getMessage());
            throw new BusinessException(ErrorCode.LOG_SOURCE_FAILED, exception.getMessage());
        }
    }

    private String appKey(PermissionService.ProjectAccess access) {
        Project project = projectMapper.selectById(access.projectId());
        return project.getAppKey();
    }

    private static String table(ClickHouseClient.Connection connection) {
        return "`" + connection.database() + "`." + LogStorageService.TABLE;
    }

    private static int discoverDays(Integer days) {
        int range = days == null ? 7 : days;
        if (range < 1 || range > DISCOVER_DAYS_MAX) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return range;
    }

    private static boolean tooLong(String text) {
        return text != null && text.length() > TEXT_MAX_LENGTH;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toRow(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("logId", row.get("log_id"));
        result.put("time", Long.parseLong(String.valueOf(row.get("time_ms"))));
        result.put("server", row.get("server"));
        result.put("actor", row.get("actor"));
        result.put("event", row.get("event"));
        result.put("retention", row.get("retention"));
        result.put("data", JsonUtils.string2Object(String.valueOf(row.get("data_json")), Map.class));
        return result;
    }

    private static String conditionSql(String relation, List<LogQueryCondition> conditions, Map<String, String> params) {
        if (conditions == null || conditions.isEmpty()) {
            return "";
        }
        if (conditions.size() > CONDITIONS_MAX || (!"and".equals(relation) && !"or".equals(relation))) {
            throw new BusinessException(ErrorCode.QUERY_CONDITION_INVALID);
        }
        List<String> parts = new ArrayList<>();
        for (int index = 0; index < conditions.size(); index++) {
            LogQueryCondition condition = conditions.get(index);
            if (condition == null) {
                throw new BusinessException(ErrorCode.QUERY_CONDITION_INVALID);
            }
            parts.add("(" + condition(field(condition.getPath(), condition.getType()), condition, "c" + index, params) + ")");
        }
        return " AND (" + String.join(" " + relation.toUpperCase() + " ", parts) + ")";
    }

    /** 一个条件：按字段类型选比较方式和参数类型；值无法按类型解析时视为条件不合法 */
    private static String condition(Field field, LogQueryCondition condition, String name, Map<String, String> params) {
        String operator = condition.getOperator() == null ? "" : condition.getOperator();
        String type = field.type();
        String expression = expression(field, name, params);
        try {
            if ("number".equals(type) || "time".equals(type)) {
                if (!COMPARE.contains(operator)) {
                    throw new BusinessException(ErrorCode.QUERY_CONDITION_INVALID);
                }
                String paramType = "number".equals(type) ? "Float64" : "Int64";
                params.put(name + "_v", number(type, condition.getValue()));
                if ("between".equals(operator)) {
                    params.put(name + "_v2", number(type, condition.getValue2()));
                    return expression + " >= {" + name + "_v:" + paramType + "} AND " + expression + " <= {" + name + "_v2:" + paramType + "}";
                }
                return expression + " " + OPERATORS.get(operator) + " {" + name + "_v:" + paramType + "}";
            }
            if ("boolean".equals(type)) {
                if (!BOOLEAN_COMPARE.contains(operator)) {
                    throw new BusinessException(ErrorCode.QUERY_CONDITION_INVALID);
                }
                String value = condition.getValue() == null ? "" : condition.getValue().trim();
                if (!"true".equals(value) && !"false".equals(value)) {
                    throw new BusinessException(ErrorCode.QUERY_CONDITION_INVALID);
                }
                params.put(name + "_v", "true".equals(value) ? "1" : "0");
                return expression + " " + OPERATORS.get(operator) + " {" + name + "_v:UInt8}";
            }
            if (!TEXT_COMPARE.contains(operator) || condition.getValue() == null) {
                throw new BusinessException(ErrorCode.QUERY_CONDITION_INVALID);
            }
            params.put(name + "_v", condition.getValue());
            if ("contains".equals(operator)) {
                return "positionCaseInsensitiveUTF8(" + expression + ", {" + name + "_v:String}) > 0";
            }
            return expression + " " + OPERATORS.get(operator) + " {" + name + "_v:String}";
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.QUERY_CONDITION_INVALID);
        }
    }

    private static String number(String type, String value) {
        if (StringUtils.isBlank(value)) {
            throw new BusinessException(ErrorCode.QUERY_CONDITION_INVALID);
        }
        String trimmed = value.trim();
        return "number".equals(type) ? String.valueOf(Double.parseDouble(trimmed)) : String.valueOf(Long.parseLong(trimmed));
    }

    /**
     * 字段取值表达式。自带列直接使用列名；data 里的字段用 JSONExtract*，路径的每一段都作为参数传入。
     * 字段不存在、为 null 或类型对不上时取 NULL，任何条件都不命中（否则会被当成 0、空文本或 false）。
     * 数字字段也接受数字文本（如 "100"）；时间统一换算成毫秒比较，可以是毫秒数，也可以是日期文本，不带时区的按 TIME_ZONE。
     */
    private static String expression(Field field, String name, Map<String, String> params) {
        String source = field.source();
        String type = field.type();
        if ("time".equals(source)) {
            return "toUnixTimestamp64Milli(time)";
        }
        if (COLUMNS.containsKey(source)) {
            String column = COLUMNS.get(source);
            return "number".equals(type) ? "toFloat64OrNull(" + column + ")" : column;
        }
        List<String> segments = Arrays.asList(source.substring("data.".length()).split("\\."));
        StringBuilder path = new StringBuilder("toJSONString(data)");
        for (int index = 0; index < segments.size(); index++) {
            params.put(name + "_k" + index, segments.get(index));
            path.append(", {").append(name).append("_k").append(index).append(":String}");
        }
        String args = path.toString();
        String kind = "JSONType(" + args + ")";
        String numeric = kind + " IN ('Int64', 'UInt64', 'Double')";
        return switch (type) {
            case "number" -> "multiIf(" + numeric + ", JSONExtractFloat(" + args + "), " + kind + " = 'String', toFloat64OrNull(JSONExtractString("
                    + args + ")), NULL)";
            case "boolean" -> "if(" + kind + " = 'Bool', JSONExtractBool(" + args + "), NULL)";
            case "time" -> "multiIf(" + kind + " = 'String', toUnixTimestamp64Milli(parseDateTime64BestEffortOrNull(JSONExtractString(" + args
                    + "), 3, '" + TIME_ZONE + "')), " + numeric + ", JSONExtractInt(" + args + "), NULL)";
            case "json" -> "if(" + kind + " = 'Null', NULL, JSONExtractRaw(" + args + "))";
            case "jsonText" -> "if(" + kind + " = 'String', JSONExtractString(" + args + "), NULL)";
            default -> "multiIf(" + kind + " = 'String', JSONExtractString(" + args + "), " + kind + " = 'Null', NULL, JSONExtractRaw(" + args + "))";
        };
    }
}
