package com.oaixdog.adminbackground.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.oaixdog.adminbackground.entity.QueryTemplate;
import com.oaixdog.adminbackground.exception.BusinessException;
import com.oaixdog.adminbackground.exception.ErrorCode;
import com.oaixdog.adminbackground.mapper.QueryTemplateMapper;
import com.oaixdog.adminbackground.protocol.QueryTemplateResponse;
import com.zfoo.protocol.util.JsonUtils;
import com.zfoo.protocol.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 数据中心查询模板：一个模板对应一个事件，字段映射决定结果列和可用的查询条件。
 * 模板管理需要 data_template 下的按钮权限；查询页面读取模板时只校验对应查询页面的权限。
 */
@Service
@RequiredArgsConstructor
public class QueryTemplateService {

    private static final String MENU = "data_template";
    private static final int NAME_MAX_LENGTH = 64;
    private static final int LABEL_MAX_LENGTH = 64;
    private static final int FIELDS_MAX_LENGTH = 65_536;
    private static final int FIELDS_MAX_COUNT = 100;
    private static final Pattern EVENT = Pattern.compile("^[a-z][a-z0-9_]{0,63}$");
    private static final Pattern KEY = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]{0,63}$");
    private static final Pattern DATA_PATH = Pattern.compile("^data(\\.[A-Za-z0-9_$-]{1,64}){1,8}$");
    private static final Set<String> TYPES = Set.of("string", "number", "boolean", "time", "json", "jsonText");
    /** 日志表自带的列，其余字段来自 data 里的 JSON 路径 */
    public static final Set<String> COLUMNS = Set.of("time", "server", "actor", "event", "logId", "retention");

    private final PermissionService permissionService;
    private final QueryTemplateMapper queryTemplateMapper;

    public record TemplateField(String key, String label, String source, String type) {
    }

    /** category 为空时是模板管理页面，否则是对应的查询页面 */
    public List<QueryTemplateResponse> list(String authorization, Long projectId, String category) {
        String menu = StringUtils.isBlank(category) ? MENU : queryMenu(category);
        permissionService.requireProjectMenu(authorization, projectId, menu);
        return queryTemplateMapper.selectList(Wrappers.<QueryTemplate>lambdaQuery().eq(QueryTemplate::getProjectId, projectId)
                        .eq(StringUtils.isNotBlank(category), QueryTemplate::getCategory, category).orderByAsc(QueryTemplate::getId))
                .stream().map(QueryTemplateResponse::valueOf).toList();
    }

    public QueryTemplateResponse create(String authorization, Long projectId, String category, String name, String event, String fields) {
        PermissionService.ProjectAccess access = permissionService.requireProjectMenu(authorization, projectId, MENU + ":create");
        QueryTemplate template = new QueryTemplate();
        template.setProjectId(access.projectId());
        fill(template, category, name, event, fields);
        long now = System.currentTimeMillis();
        template.setCreatedAt(now);
        template.setUpdatedAt(now);
        requireUniqueName(template);
        try {
            queryTemplateMapper.insert(template);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.QUERY_TEMPLATE_NAME_EXISTS);
        }
        return QueryTemplateResponse.valueOf(template);
    }

    public QueryTemplateResponse update(String authorization, Long projectId, Long templateId, String category, String name, String event, String fields) {
        PermissionService.ProjectAccess access = permissionService.requireProjectMenu(authorization, projectId, MENU + ":update");
        QueryTemplate template = requireTemplate(access.projectId(), templateId);
        fill(template, category, name, event, fields);
        template.setUpdatedAt(System.currentTimeMillis());
        requireUniqueName(template);
        try {
            queryTemplateMapper.updateById(template);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.QUERY_TEMPLATE_NAME_EXISTS);
        }
        return QueryTemplateResponse.valueOf(template);
    }

    public void remove(String authorization, Long projectId, Long templateId) {
        PermissionService.ProjectAccess access = permissionService.requireProjectMenu(authorization, projectId, MENU + ":remove");
        QueryTemplate template = requireTemplate(access.projectId(), templateId);
        queryTemplateMapper.deleteById(template.getId());
    }

    /** 模板必须属于本次校验通过的项目 */
    public QueryTemplate requireTemplate(long projectId, Long templateId) {
        QueryTemplate template = templateId == null ? null : queryTemplateMapper.selectById(templateId);
        if (template == null || !Objects.equals(template.getProjectId(), projectId)) {
            throw new BusinessException(ErrorCode.QUERY_TEMPLATE_NOT_FOUND);
        }
        return template;
    }

    public static String queryMenu(String category) {
        return switch (category) {
            case QueryTemplate.CATEGORY_DATA -> "data_query";
            case QueryTemplate.CATEGORY_LOG -> "data_log";
            default -> throw new BusinessException(ErrorCode.INVALID_REQUEST);
        };
    }

    /** 解析并校验字段映射；字段里的其他配置（如 JSON 子字段映射）只给前端使用，原样保存 */
    @SuppressWarnings("unchecked")
    public static List<TemplateField> parseFields(String fields) {
        if (StringUtils.isBlank(fields) || fields.length() > FIELDS_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.QUERY_TEMPLATE_INVALID);
        }
        List<Map> items;
        try {
            items = JsonUtils.string2List(fields, Map.class);
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.QUERY_TEMPLATE_INVALID);
        }
        if (items == null || items.isEmpty() || items.size() > FIELDS_MAX_COUNT) {
            throw new BusinessException(ErrorCode.QUERY_TEMPLATE_INVALID);
        }
        List<TemplateField> result = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (Map<String, Object> item : items) {
            if (item == null) {
                throw new BusinessException(ErrorCode.QUERY_TEMPLATE_INVALID);
            }
            String key = text(item.get("key"));
            String label = text(item.get("label"));
            String source = text(item.get("source"));
            String type = text(item.get("type"));
            boolean column = COLUMNS.contains(source);
            boolean validSource = column ? validColumnType(source, type) : DATA_PATH.matcher(source).matches();
            if (!KEY.matcher(key).matches() || !keys.add(key) || label.isEmpty() || label.length() > LABEL_MAX_LENGTH
                    || !TYPES.contains(type) || !validSource) {
                throw new BusinessException(ErrorCode.QUERY_TEMPLATE_INVALID);
            }
            result.add(new TemplateField(key, label, source, type));
        }
        return result;
    }

    /** 时间列只能按时间处理，其余自带列是文本，也可以按数字比较 */
    private static boolean validColumnType(String column, String type) {
        return "time".equals(column) ? "time".equals(type) : "string".equals(type) || "number".equals(type);
    }

    private static String text(Object value) {
        return value instanceof String string ? string.trim() : "";
    }

    private void fill(QueryTemplate template, String category, String name, String event, String fields) {
        String templateName = name == null ? "" : name.trim();
        String templateEvent = event == null ? "" : event.trim();
        if (category == null || templateName.isEmpty() || templateName.length() > NAME_MAX_LENGTH || !EVENT.matcher(templateEvent).matches()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        queryMenu(category);
        parseFields(fields);
        template.setCategory(category);
        template.setName(templateName);
        template.setEvent(templateEvent);
        template.setFields(fields);
    }

    private void requireUniqueName(QueryTemplate template) {
        if (queryTemplateMapper.selectCount(Wrappers.<QueryTemplate>lambdaQuery().eq(QueryTemplate::getProjectId, template.getProjectId())
                .eq(QueryTemplate::getName, template.getName()).ne(template.getId() != null, QueryTemplate::getId, template.getId())) > 0) {
            throw new BusinessException(ErrorCode.QUERY_TEMPLATE_NAME_EXISTS);
        }
    }
}
