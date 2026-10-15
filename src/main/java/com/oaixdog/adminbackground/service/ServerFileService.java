package com.oaixdog.adminbackground.service;

import com.oaixdog.adminbackground.entity.ServerHost;
import com.oaixdog.adminbackground.entity.ServerProcess;
import com.oaixdog.adminbackground.exception.BusinessException;
import com.oaixdog.adminbackground.exception.ErrorCode;
import com.zfoo.protocol.util.JsonUtils;
import com.zfoo.protocol.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 进程文件管理：浏览、读取、保存、删除工作目录下的文件。页面发起的操作放进 Redis，Agent 下一次心跳领取执行后回传结果，页面轮询结果。
 * 路径都是相对工作目录的路径，Agent 会再次确认不超出工作目录。
 */
@Service
@RequiredArgsConstructor
public class ServerFileService {

    private static final String MENU = "server_process:file";
    private static final String OP_KEY = "server:file:op:";
    private static final String QUEUE_KEY = "server:file:queue:";
    private static final String RESULT_KEY = "server:file:result:";
    private static final Duration OP_TTL = Duration.ofMinutes(2);
    private static final long RESULT_TIMEOUT = 60_000L;
    private static final Set<String> OPS = Set.of("list", "read", "write", "delete");
    private static final Pattern OP_ID = Pattern.compile("^[0-9a-f]{32}$");
    private static final int PATH_MAX_LENGTH = 512;
    private static final int CONTENT_MAX_LENGTH = 1024 * 1024;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PermissionService permissionService;
    private final ServerProcessService processService;
    private final ServerMetricService metricService;
    private final RedisService redisService;
    private final AgentWaitService waitService;

    public Map<String, Object> request(String authorization, Long projectId, Long processId, String op, String path, String content) {
        PermissionService.ProjectAccess access = permissionService.requireProjectMenu(authorization, projectId, MENU);
        ServerProcess process = processService.require(access.projectId(), processId);
        String relative = normalize(path);
        String text = "write".equals(op) ? (content == null ? "" : content) : "";
        if (!OPS.contains(op) || relative == null || text.length() > CONTENT_MAX_LENGTH || ("delete".equals(op) || "write".equals(op)) && relative.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (!metricService.isOnline(process.getServerId())) {
            throw new BusinessException(ErrorCode.SERVER_OFFLINE);
        }
        byte[] random = new byte[16];
        RANDOM.nextBytes(random);
        String opId = HexFormat.of().formatHex(random);
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("projectId", process.getProjectId());
        value.put("serverId", process.getServerId());
        value.put("processId", process.getId());
        value.put("op", op);
        value.put("path", relative);
        value.put("content", text);
        value.put("createdAt", System.currentTimeMillis());
        redisService.set(OP_KEY + opId, JsonUtils.object2String(value), OP_TTL);
        redisService.addToSet(QUEUE_KEY + process.getServerId(), opId, OP_TTL);
        waitService.wake(process.getServerId());
        return Map.of("opId", opId);
    }

    public Map<String, Object> result(String authorization, Long projectId, String opId) {
        PermissionService.ProjectAccess access = permissionService.requireProjectMenu(authorization, projectId, MENU);
        Map<String, Object> op = op(opId);
        if (op == null || ((Number) op.get("projectId")).longValue() != access.projectId()) {
            throw new BusinessException(ErrorCode.FILE_OP_NOT_FOUND);
        }
        String resultText = redisService.get(RESULT_KEY + opId);
        Map<String, Object> result = new LinkedHashMap<>();
        if (resultText == null) {
            boolean timeout = System.currentTimeMillis() - ((Number) op.get("createdAt")).longValue() > RESULT_TIMEOUT;
            result.put("status", timeout ? "timeout" : "pending");
            result.put("success", false);
            result.put("message", timeout ? "操作超时，请确认服务器在线" : "");
            result.put("data", null);
            return result;
        }
        result.put("status", "done");
        result.putAll(JsonUtils.string2Object(resultText, LinkedHashMap.class));
        return result;
    }

    /** Agent 心跳领取本服务器的文件操作 */
    public List<Map<String, Object>> dispatch(long serverId) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (String opId : redisService.members(QUEUE_KEY + serverId)) {
            redisService.removeFromSet(QUEUE_KEY + serverId, opId);
            Map<String, Object> op = op(opId);
            if (op == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("opId", opId);
            item.put("processId", op.get("processId"));
            item.put("op", op.get("op"));
            item.put("path", op.get("path"));
            item.put("content", op.get("content"));
            result.add(item);
        }
        return result;
    }

    /** Agent 回传结果，data 为 JSON 文本 */
    public void agentResult(ServerHost server, String opId, Boolean success, String message, String data) {
        Map<String, Object> op = op(opId);
        if (op == null || ((Number) op.get("serverId")).longValue() != server.getId()) {
            throw new BusinessException(ErrorCode.FILE_OP_NOT_FOUND);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", Boolean.TRUE.equals(success));
        result.put("message", message == null ? "" : message);
        result.put("data", StringUtils.isBlank(data) ? null : JsonUtils.string2Object(data, LinkedHashMap.class));
        redisService.set(RESULT_KEY + opId, JsonUtils.object2String(result), OP_TTL);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> op(String opId) {
        String text = opId == null || !OP_ID.matcher(opId).matches() ? null : redisService.get(OP_KEY + opId);
        return text == null ? null : JsonUtils.string2Object(text, LinkedHashMap.class);
    }

    /** 相对工作目录的路径：去掉首尾的 /，不允许 ..、反斜杠和控制字符；不合法返回 null */
    private static String normalize(String path) {
        String text = path == null ? "" : path.trim().replaceAll("^/+|/+$", "");
        if (text.length() > PATH_MAX_LENGTH || text.contains("\\") || text.matches(".*\\p{Cntrl}.*")) {
            return null;
        }
        for (String segment : text.split("/")) {
            if (!text.isEmpty() && (segment.isEmpty() || segment.equals(".") || segment.equals(".."))) {
                return null;
            }
        }
        return text;
    }
}
