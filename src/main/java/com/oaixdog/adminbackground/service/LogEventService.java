package com.oaixdog.adminbackground.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.oaixdog.adminbackground.entity.LogEvent;
import com.oaixdog.adminbackground.exception.BusinessException;
import com.oaixdog.adminbackground.exception.ErrorCode;
import com.oaixdog.adminbackground.mapper.LogEventMapper;
import com.zfoo.protocol.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 事件字典：给事件和字段起中文名、指定展示类型，按项目保存，不区分日志源。只影响展示，没有配置的事件也能查询。
 * 读取需要日志查询或事件管理页面；修改、删除需要 log_event:update、log_event:remove。
 */
@Service
@RequiredArgsConstructor
public class LogEventService {

    private static final String MENU = "log_event";
    private static final int LABEL_MAX_LENGTH = 64;
    private static final int DESCRIPTION_MAX_LENGTH = 256;
    private static final int FIELDS_MAX_LENGTH = 65_536;
    private static final int FIELDS_MAX = 200;
    public static final Pattern EVENT = Pattern.compile("^[a-z][a-z0-9_]{0,63}$");
    private static final Pattern PATH = Pattern.compile("^[A-Za-z0-9_$-]{1,64}(\\.[A-Za-z0-9_$-]{1,64}){0,7}$");
    private static final Set<String> TYPES = Set.of("string", "number", "boolean", "time", "json", "jsonText");

    private final PermissionService permissionService;
    private final LogEventMapper logEventMapper;

    public List<Map<String, Object>> list(String authorization, Long projectId) {
        PermissionService.ProjectAccess access = permissionService.requireAnyProjectMenu(authorization, projectId, "log_query", MENU);
        return logEventMapper.selectList(Wrappers.<LogEvent>lambdaQuery().eq(LogEvent::getProjectId, access.projectId()).orderByAsc(LogEvent::getEvent))
                .stream().map(LogEventService::toResponse).toList();
    }

    /** 按事件名新增或覆盖 */
    public Map<String, Object> save(String authorization, Long projectId, String event, String label, String description, String fields) {
        PermissionService.ProjectAccess access = permissionService.requireProjectMenu(authorization, projectId, MENU + ":update");
        String eventName = event == null ? "" : event.trim();
        String eventLabel = label == null ? "" : label.trim();
        String eventDescription = description == null ? "" : description.trim();
        String eventFields = fields == null || fields.isBlank() ? "[]" : fields;
        if (!EVENT.matcher(eventName).matches() || eventLabel.length() > LABEL_MAX_LENGTH || eventDescription.length() > DESCRIPTION_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        validateFields(eventFields);
        LogEvent entry = logEventMapper.selectOne(Wrappers.<LogEvent>lambdaQuery().eq(LogEvent::getProjectId, access.projectId()).eq(LogEvent::getEvent, eventName));
        long now = System.currentTimeMillis();
        if (entry == null) {
            entry = new LogEvent();
            entry.setProjectId(access.projectId());
            entry.setEvent(eventName);
            entry.setCreatedAt(now);
        }
        entry.setLabel(eventLabel);
        entry.setDescription(eventDescription);
        entry.setFields(eventFields);
        entry.setUpdatedAt(now);
        if (entry.getId() == null) {
            logEventMapper.insert(entry);
        } else {
            logEventMapper.updateById(entry);
        }
        return toResponse(entry);
    }

    public void remove(String authorization, Long projectId, String event) {
        PermissionService.ProjectAccess access = permissionService.requireProjectMenu(authorization, projectId, MENU + ":remove");
        logEventMapper.delete(Wrappers.<LogEvent>lambdaQuery().eq(LogEvent::getProjectId, access.projectId()).eq(LogEvent::getEvent, event));
    }

    /** 字段说明：path 为 data 里的路径（点号分隔），唯一；label 可空；type 为展示类型 */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void validateFields(String fields) {
        if (fields.length() > FIELDS_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.LOG_EVENT_INVALID);
        }
        List<Map> items;
        try {
            items = JsonUtils.string2List(fields, Map.class);
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.LOG_EVENT_INVALID);
        }
        if (items == null || items.size() > FIELDS_MAX || items.stream().anyMatch(Objects::isNull)) {
            throw new BusinessException(ErrorCode.LOG_EVENT_INVALID);
        }
        Set<String> paths = new HashSet<>();
        for (Map<String, Object> item : items) {
            String path = item.get("path") instanceof String text ? text.trim() : "";
            String label = item.get("label") instanceof String text ? text.trim() : "";
            String type = item.get("type") instanceof String text ? text : "";
            if (!PATH.matcher(path).matches() || !paths.add(path) || label.length() > LABEL_MAX_LENGTH || !TYPES.contains(type)) {
                throw new BusinessException(ErrorCode.LOG_EVENT_INVALID);
            }
        }
    }

    private static Map<String, Object> toResponse(LogEvent entry) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("event", entry.getEvent());
        result.put("label", entry.getLabel());
        result.put("description", entry.getDescription());
        result.put("fields", entry.getFields());
        result.put("updatedAt", entry.getUpdatedAt());
        return result;
    }
}
