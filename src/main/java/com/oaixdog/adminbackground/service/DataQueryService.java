package com.oaixdog.adminbackground.service;

import com.oaixdog.adminbackground.entity.DataSource;
import com.oaixdog.adminbackground.entity.DataTemplate;
import com.oaixdog.adminbackground.exception.BusinessException;
import com.oaixdog.adminbackground.exception.ErrorCode;
import com.oaixdog.adminbackground.protocol.DataQueryResponse;
import com.zfoo.protocol.util.JsonUtils;
import com.zfoo.protocol.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletableFuture;

/**
 * 数据查询：按模板调用业务服务器的查询接口。参数按模板定义校验并转换类型，模板里没有定义的参数不会发给业务服务器；
 * 结果行原样返回，由页面按模板的结果字段展示。查询需要 data_query，导出需要 data_query:export。
 */
@Service
@RequiredArgsConstructor
public class DataQueryService {

    private static final String MENU = "data_query";
    private static final int PAGE_SIZE_MAX = 200;
    private static final int EXPORT_MAX = 50_000;
    private static final int EXPORT_PAGE_SIZE = 1_000;
    private static final int PARAMS_MAX_LENGTH = 16_384;
    private static final int TEXT_MAX_LENGTH = 512;
    private static final String OFFLINE = "区服不在线";

    private final PermissionService permissionService;
    private final DataTemplateService dataTemplateService;
    private final DataSourceService dataSourceService;
    private final BusinessClient businessClient;

    private static final ExecutorService ZONE_EXECUTOR = Executors.newFixedThreadPool(8);

    /** 固定地址：targets 只有一个，键为 null；按区自动发现：区 ID → 目标，不在线的区目标为 null */
    private record Call(Map<Integer, BusinessClient.Target> targets, boolean zone, String query, Map<String, Object> params) {
    }

    public DataQueryResponse query(String authorization, Long projectId, Long templateId, List<Long> zoneIds, String params, Integer page, Integer pageSize) {
        PermissionService.ProjectAccess access = permissionService.requireProjectMenu(authorization, projectId, MENU);
        int pageNumber = page == null ? 1 : page;
        int size = pageSize == null ? 20 : pageSize;
        if (pageNumber < 1 || size < 1 || size > PAGE_SIZE_MAX) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        Call call = prepare(access, templateId, zoneIds, params);
        if (!call.zone()) {
            return fetch(call.targets().get(null), call, pageNumber, size);
        }
        // 多个区同时查询，每个区取同一页，按区 ID 顺序合并；失败或离线的区记在 failures
        Map<Integer, CompletableFuture<DataQueryResponse>> futures = new LinkedHashMap<>();
        call.targets().forEach((zoneId, target) -> futures.put(zoneId, target == null ? null
                : CompletableFuture.supplyAsync(() -> fetch(target, call, pageNumber, size), ZONE_EXECUTOR)));
        DataQueryResponse merged = new DataQueryResponse();
        List<Map<String, Object>> rows = new ArrayList<>();
        List<Map<String, Object>> failures = new ArrayList<>();
        long total = 0;
        for (Map.Entry<Integer, CompletableFuture<DataQueryResponse>> entry : futures.entrySet()) {
            if (entry.getValue() == null) {
                failures.add(Map.of("zoneId", entry.getKey(), "message", OFFLINE));
                continue;
            }
            try {
                DataQueryResponse response = entry.getValue().join();
                total += response.getTotal();
                rows.addAll(markZone(response.getList(), entry.getKey()));
            } catch (BusinessException | CompletionException exception) {
                failures.add(Map.of("zoneId", entry.getKey(), "message", message(exception)));
            }
        }
        merged.setTotal(total);
        merged.setList(rows);
        merged.setFailures(failures);
        return merged;
    }

    /** 按页依次取，最多 5 万行；业务服务器返回空页时停止 */
    public DataQueryResponse export(String authorization, Long projectId, Long templateId, List<Long> zoneIds, String params) {
        permissionService.requireProjectMenu(authorization, projectId, MENU + ":export");
        PermissionService.ProjectAccess access = permissionService.requireProjectMenu(authorization, projectId, MENU);
        Call call = prepare(access, templateId, zoneIds, params);
        List<Map<String, Object>> rows = new ArrayList<>();
        List<Map<String, Object>> failures = new ArrayList<>();
        long total = 0;
        for (Map.Entry<Integer, BusinessClient.Target> entry : call.targets().entrySet()) {
            if (rows.size() >= EXPORT_MAX) {
                break;
            }
            if (entry.getValue() == null) {
                failures.add(Map.of("zoneId", entry.getKey(), "message", OFFLINE));
                continue;
            }
            try {
                List<Map<String, Object>> zoneRows = new ArrayList<>();
                for (int page = 1; rows.size() + zoneRows.size() < EXPORT_MAX; page++) {
                    DataQueryResponse response = fetch(entry.getValue(), call, page, EXPORT_PAGE_SIZE);
                    zoneRows.addAll(response.getList());
                    if (response.getList().isEmpty() || zoneRows.size() >= response.getTotal()) {
                        total += response.getTotal();
                        break;
                    }
                }
                rows.addAll(call.zone() ? markZone(zoneRows, entry.getKey()) : zoneRows);
            } catch (BusinessException exception) {
                if (!call.zone()) {
                    throw exception;
                }
                failures.add(Map.of("zoneId", entry.getKey(), "message", message(exception)));
            }
        }
        DataQueryResponse response = new DataQueryResponse();
        response.setTotal(total);
        response.setList(rows.size() > EXPORT_MAX ? rows.subList(0, EXPORT_MAX) : rows);
        response.setFailures(failures);
        return response;
    }

    private Call prepare(PermissionService.ProjectAccess access, Long templateId, List<Long> zoneIds, String params) {
        DataTemplate template = dataTemplateService.requireTemplate(access.projectId(), templateId);
        DataSource source = dataSourceService.requireSource(access.projectId(), template.getSourceId());
        Map<String, Object> values = convert(DataTemplateService.parseParams(template.getParams()), params);
        if (!DataSource.TYPE_ZONE.equals(source.getType())) {
            Map<Integer, BusinessClient.Target> targets = new LinkedHashMap<>();
            targets.put(null, dataSourceService.target(access, source));
            return new Call(targets, false, template.getQuery(), values);
        }
        if (zoneIds == null || zoneIds.isEmpty() || zoneIds.contains(null)) {
            throw new BusinessException(ErrorCode.DATA_QUERY_PARAM_INVALID, "请选择区服");
        }
        return new Call(dataSourceService.zoneTargets(access, source, zoneIds), true, template.getQuery(), values);
    }

    /** 每一行加上区 ID（_zone），页面显示「区服」列 */
    private static List<Map<String, Object>> markZone(List<Map<String, Object>> rows, int zoneId) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>(row);
            item.put("_zone", zoneId);
            result.add(item);
        }
        return result;
    }

    private static String message(Exception exception) {
        Throwable cause = exception instanceof CompletionException && exception.getCause() != null ? exception.getCause() : exception;
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    @SuppressWarnings("unchecked")
    private DataQueryResponse fetch(BusinessClient.Target target, Call call, int page, int pageSize) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", call.query());
        body.put("params", call.params());
        body.put("page", page);
        body.put("pageSize", pageSize);
        Map<String, Object> data = businessClient.call(target, DataSourceService.QUERY_PATH, body);
        List<Map<String, Object>> list = new ArrayList<>();
        if (data.get("list") instanceof List<?> items) {
            for (Object item : items) {
                if (item instanceof Map<?, ?> row) {
                    list.add((Map<String, Object>) row);
                }
            }
        }
        DataQueryResponse response = new DataQueryResponse();
        response.setTotal(data.get("total") instanceof Number number ? number.longValue() : list.size());
        response.setList(list);
        return response;
    }

    /** 按参数定义转换：必填检查；数字、布尔、时间（毫秒）转为对应类型；下拉只能是定义的选项；空值不传 */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> convert(List<DataTemplateService.Param> definitions, String params) {
        if (params != null && params.length() > PARAMS_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.DATA_QUERY_PARAM_INVALID);
        }
        Map<String, Object> input;
        try {
            input = StringUtils.isBlank(params) ? Map.of() : JsonUtils.string2Object(params, Map.class);
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.DATA_QUERY_PARAM_INVALID);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (DataTemplateService.Param param : definitions) {
            Object raw = input.get(param.name());
            String text = raw == null ? "" : String.valueOf(raw).trim();
            if (text.isEmpty()) {
                if (param.required()) {
                    throw new BusinessException(ErrorCode.DATA_QUERY_PARAM_INVALID, "请填写「" + param.label() + "」");
                }
                continue;
            }
            try {
                Object value = switch (param.type()) {
                    case "number" -> raw instanceof Number number ? number : Double.parseDouble(text);
                    case "time" -> raw instanceof Number number ? number.longValue() : Long.parseLong(text);
                    case "boolean" -> {
                        if (!"true".equals(text) && !"false".equals(text)) {
                            throw new NumberFormatException();
                        }
                        yield Boolean.parseBoolean(text);
                    }
                    case "select" -> {
                        if (!param.options().contains(text)) {
                            throw new NumberFormatException();
                        }
                        yield text;
                    }
                    default -> {
                        if (text.length() > TEXT_MAX_LENGTH) {
                            throw new NumberFormatException();
                        }
                        yield text;
                    }
                };
                result.put(param.name(), value);
            } catch (NumberFormatException exception) {
                throw new BusinessException(ErrorCode.DATA_QUERY_PARAM_INVALID, "「" + param.label() + "」的值不合法");
            }
        }
        return result;
    }
}
