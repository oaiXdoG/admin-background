package com.oaixdog.adminbackground.service;

import com.oaixdog.adminbackground.entity.Project;
import com.oaixdog.adminbackground.entity.QueryTemplate;
import com.oaixdog.adminbackground.exception.BusinessException;
import com.oaixdog.adminbackground.exception.ErrorCode;
import com.oaixdog.adminbackground.mapper.ProjectMapper;
import com.oaixdog.adminbackground.protocol.DataQueryCondition;
import com.oaixdog.adminbackground.protocol.DataQueryResponse;
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

/**
 * 数据中心查询：按模板事件查询 ClickHouse 日志表。
 * 项目 App Key 由服务器按本次校验通过的项目填入，时间范围必填；字段路径和条件值全部走查询参数，SQL 里只拼固定的表达式。
 */
@Service
@RequiredArgsConstructor
public class DataQueryService {

    private static final Logger log = LoggerFactory.getLogger(DataQueryService.class);

    private static final int PAGE_SIZE_MAX = 200;
    private static final int OFFSET_MAX = 10_000;
    private static final int CONDITIONS_MAX = 20;
    private static final Map<String, String> SETTINGS = Map.of("max_execution_time", "15", "readonly", "2");
    private static final Set<String> COMPARE = Set.of("eq", "ne", "gt", "ge", "lt", "le", "between");
    private static final Set<String> TEXT_COMPARE = Set.of("eq", "ne", "contains");
    private static final Set<String> BOOLEAN_COMPARE = Set.of("eq", "ne");
    private static final Map<String, String> OPERATORS = Map.of("eq", "=", "ne", "!=", "gt", ">", "ge", ">=", "lt", "<", "le", "<=");
    private static final Map<String, String> COLUMNS = Map.of("server", "server", "actor", "actor", "event", "event", "logId", "log_id", "retention", "retention");

    private final PermissionService permissionService;
    private final QueryTemplateService queryTemplateService;
    private final LogStorageService logStorageService;
    private final ClickHouseClient clickHouseClient;
    private final ProjectMapper projectMapper;

    public DataQueryResponse query(String authorization, Long projectId, Long templateId, Long startTime, Long endTime, String relation,
                                   List<DataQueryCondition> conditions, Integer page, Integer pageSize) {
        permissionService.requireAccount(authorization);
        if (projectId == null || templateId == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        // 先按模板分类确定页面权限：模板归属在校验之后再按通过的项目确认
        QueryTemplate template = queryTemplateService.requireTemplate(projectId, templateId);
        PermissionService.ProjectAccess access = permissionService.requireProjectMenu(authorization, projectId, QueryTemplateService.queryMenu(template.getCategory()));
        Project project = projectMapper.selectById(access.projectId());
        int pageNumber = page == null ? 1 : page;
        int size = pageSize == null ? 20 : pageSize;
        if (startTime == null || endTime == null || startTime >= endTime || pageNumber < 1 || size < 1 || size > PAGE_SIZE_MAX
                || (long) (pageNumber - 1) * size > OFFSET_MAX) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        ClickHouseClient.Connection connection = logStorageService.requireReader();
        Map<String, String> params = new HashMap<>();
        params.put("project", project.getAppKey());
        params.put("event", template.getEvent());
        params.put("start", String.valueOf(startTime));
        params.put("end", String.valueOf(endTime));
        String where = "project = {project:String} AND event = {event:String}"
                + " AND time >= fromUnixTimestamp64Milli({start:Int64}) AND time < fromUnixTimestamp64Milli({end:Int64})"
                + conditionSql(QueryTemplateService.parseFields(template.getFields()), relation, conditions, params);
        String table = "`" + connection.database() + "`." + LogStorageService.TABLE;
        try {
            List<Map<String, Object>> count = clickHouseClient.query(connection, "SELECT count() AS total FROM " + table + " WHERE " + where, params, SETTINGS);
            Map<String, String> pageParams = new HashMap<>(params);
            pageParams.put("limit", String.valueOf(size));
            pageParams.put("offset", String.valueOf((pageNumber - 1) * size));
            List<Map<String, Object>> rows = clickHouseClient.query(connection,
                    "SELECT log_id, toUnixTimestamp64Milli(time) AS time_ms, server, actor, event, retention, toJSONString(data) AS data_json FROM "
                            + table + " WHERE " + where + " ORDER BY time DESC LIMIT {limit:UInt32} OFFSET {offset:UInt32}", pageParams, SETTINGS);
            DataQueryResponse response = new DataQueryResponse();
            response.setDatabase(connection.database());
            response.setTotal(count.isEmpty() ? 0 : Long.parseLong(String.valueOf(count.getFirst().get("total"))));
            response.setList(rows.stream().map(DataQueryService::toRow).toList());
            return response;
        } catch (ClickHouseClient.ClickHouseException exception) {
            log.warn("数据中心查询失败，项目 {}，模板 {}: {}", access.projectId(), templateId, exception.getMessage());
            throw new BusinessException(ErrorCode.LOG_STORAGE_UNAVAILABLE);
        }
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

    private static String conditionSql(List<QueryTemplateService.TemplateField> fields, String relation,
                                       List<DataQueryCondition> conditions, Map<String, String> params) {
        if (conditions == null || conditions.isEmpty()) {
            return "";
        }
        if (conditions.size() > CONDITIONS_MAX || (!"and".equals(relation) && !"or".equals(relation))) {
            throw new BusinessException(ErrorCode.QUERY_CONDITION_INVALID);
        }
        List<String> parts = new ArrayList<>();
        for (int index = 0; index < conditions.size(); index++) {
            DataQueryCondition condition = conditions.get(index);
            QueryTemplateService.TemplateField field = condition == null ? null : fields.stream()
                    .filter(item -> item.key().equals(condition.getField())).findFirst().orElse(null);
            if (field == null) {
                throw new BusinessException(ErrorCode.QUERY_CONDITION_INVALID);
            }
            parts.add("(" + condition(field, condition, "c" + index, params) + ")");
        }
        return " AND (" + String.join(" " + relation.toUpperCase() + " ", parts) + ")";
    }

    /** 一个条件：按字段类型选比较方式和参数类型；值无法按类型解析时视为条件不合法 */
    private static String condition(QueryTemplateService.TemplateField field, DataQueryCondition condition, String name, Map<String, String> params) {
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
     * 时间统一换算成毫秒比较：data 里的时间可以是毫秒数，也可以是日期文本。
     */
    private static String expression(QueryTemplateService.TemplateField field, String name, Map<String, String> params) {
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
        return switch (type) {
            case "number" -> "JSONExtractFloat(" + args + ")";
            case "boolean" -> "JSONExtractBool(" + args + ")";
            case "time" -> "if(JSONType(" + args + ") = 'String', toUnixTimestamp64Milli(parseDateTime64BestEffortOrNull(JSONExtractString("
                    + args + "), 3)), JSONExtractInt(" + args + "))";
            case "json" -> "JSONExtractRaw(" + args + ")";
            case "jsonText" -> "JSONExtractString(" + args + ")";
            default -> "if(JSONType(" + args + ") = 'String', JSONExtractString(" + args + "), JSONExtractRaw(" + args + "))";
        };
    }
}
