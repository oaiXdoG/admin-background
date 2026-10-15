package com.oaixdog.adminbackground.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.oaixdog.adminbackground.config.ApplicationProperties;
import com.oaixdog.adminbackground.entity.ServerHost;
import com.oaixdog.adminbackground.entity.ServerProcess;
import com.oaixdog.adminbackground.exception.BusinessException;
import com.oaixdog.adminbackground.exception.ErrorCode;
import com.oaixdog.adminbackground.mapper.ServerHostMapper;
import com.oaixdog.adminbackground.mapper.ServerProcessMapper;
import com.zfoo.protocol.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 服务器：登记后生成一次性安装命令，运维在服务器上执行后 Agent 用令牌注册（见 AgentService）。
 * 状态：没有注册过为 pending，30 秒内有心跳为 online，否则 offline。
 */
@Service
@RequiredArgsConstructor
public class ServerHostService {

    private static final String MENU = "server_host";
    private static final String PROCESS_MENU = "server_process";
    private static final String MONITOR_MENU = "server_monitor";
    private static final long METRIC_RANGE_MAX = Duration.ofDays(7).plusHours(1).toMillis();
    private static final Set<Integer> METRIC_STEPS = Set.of(10, 60, 300, 3600);
    private static final int METRIC_POINTS_MAX = 2100;
    private static final int NAME_MAX_LENGTH = 64;
    private static final int GROUP_MAX_LENGTH = 64;
    private static final int REMARK_MAX_LENGTH = 128;
    public static final Duration INSTALL_TOKEN_TTL = Duration.ofMinutes(10);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PermissionService permissionService;
    private final ServerHostMapper hostMapper;
    private final ServerProcessMapper processMapper;
    private final ServerMetricService metricService;
    private final RedisService redisService;
    private final ApplicationProperties properties;

    public List<Map<String, Object>> list(String authorization, Long projectId) {
        PermissionService.ProjectAccess access = permissionService.requireAnyProjectMenu(authorization, projectId, MENU, PROCESS_MENU);
        return hostMapper.selectList(Wrappers.<ServerHost>lambdaQuery().eq(ServerHost::getProjectId, access.projectId())
                .orderByAsc(ServerHost::getGroupName).orderByAsc(ServerHost::getName)).stream().map(this::toResponse).toList();
    }

    public Map<String, Object> create(String authorization, Long projectId, String name, String groupName, String remark) {
        PermissionService.ProjectAccess access = permissionService.requireProjectMenu(authorization, projectId, MENU + ":create");
        ServerHost server = new ServerHost();
        server.setProjectId(access.projectId());
        fill(server, name, groupName, remark);
        long now = System.currentTimeMillis();
        server.setSecret("");
        server.setHostname("");
        server.setIp("");
        server.setPrivateIp("");
        server.setOs("");
        server.setCpuCores(0);
        server.setMemoryTotal(0L);
        server.setAgentVersion("");
        server.setRegisteredAt(0L);
        server.setCreatedAt(now);
        server.setUpdatedAt(now);
        requireUniqueName(server);
        try {
            hostMapper.insert(server);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.SERVER_NAME_EXISTS);
        }
        return Map.of("server", toResponse(server), "installCommand", installCommand(server.getId()));
    }

    public Map<String, Object> update(String authorization, Long projectId, Long serverId, String name, String groupName, String remark) {
        PermissionService.ProjectAccess access = permissionService.requireProjectMenu(authorization, projectId, MENU + ":update");
        ServerHost server = require(access.projectId(), serverId);
        fill(server, name, groupName, remark);
        server.setUpdatedAt(System.currentTimeMillis());
        requireUniqueName(server);
        try {
            hostMapper.updateById(server);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.SERVER_NAME_EXISTS);
        }
        return toResponse(server);
    }

    public void remove(String authorization, Long projectId, Long serverId) {
        PermissionService.ProjectAccess access = permissionService.requireProjectMenu(authorization, projectId, MENU + ":remove");
        ServerHost server = require(access.projectId(), serverId);
        if (processMapper.selectCount(Wrappers.<ServerProcess>lambdaQuery().eq(ServerProcess::getServerId, server.getId())) > 0) {
            throw new BusinessException(ErrorCode.SERVER_IN_USE);
        }
        hostMapper.deleteById(server.getId());
    }

    public String install(String authorization, Long projectId, Long serverId) {
        PermissionService.ProjectAccess access = permissionService.requireProjectMenu(authorization, projectId, MENU + ":update");
        return installCommand(require(access.projectId(), serverId).getId());
    }

    /** 监控曲线：时间范围不超过 7 天（数据只保留 7 天），粒度为 10 秒、1 分钟、5 分钟或 1 小时，每条曲线最多 2000 个点 */
    public List<Map<String, Object>> metrics(String authorization, Long projectId, Long serverId, Long processId, Long startTime, Long endTime, Integer step) {
        PermissionService.ProjectAccess access = permissionService.requireProjectMenu(authorization, projectId, MONITOR_MENU);
        ServerHost server = require(access.projectId(), serverId);
        if (processId != null) {
            ServerProcess process = processMapper.selectById(processId);
            if (process == null || !Objects.equals(process.getServerId(), server.getId())) {
                throw new BusinessException(ErrorCode.PROCESS_NOT_FOUND);
            }
        }
        boolean valid = startTime != null && endTime != null && startTime < endTime && endTime - startTime <= METRIC_RANGE_MAX
                && step != null && METRIC_STEPS.contains(step) && (endTime - startTime) / (step * 1000L) <= METRIC_POINTS_MAX;
        if (!valid) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        long process = processId == null ? 0L : processId;
        return metricService.query(server.getId(), process, startTime, endTime, step);
    }

    public ServerHost require(long projectId, Long serverId) {
        ServerHost server = serverId == null ? null : hostMapper.selectById(serverId);
        if (server == null || !Objects.equals(server.getProjectId(), projectId)) {
            throw new BusinessException(ErrorCode.SERVER_NOT_FOUND);
        }
        return server;
    }

    public static String status(ServerHost server, boolean online) {
        if (StringUtils.isBlank(server.getSecret())) {
            return "pending";
        }
        return online ? "online" : "offline";
    }

    /** 一次性令牌 10 分钟有效，Agent 注册时换取服务器 ID 和密钥 */
    private String installCommand(long serverId) {
        String baseUrl = properties.getAgentBaseUrl().replaceAll("/+$", "");
        if (StringUtils.isBlank(baseUrl)) {
            throw new IllegalStateException("未配置 agent.base-url");
        }
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes);
        redisService.set(AgentService.INSTALL_KEY + token, String.valueOf(serverId), INSTALL_TOKEN_TTL);
        return "curl -fsSL " + baseUrl + "/agent/install.sh | bash -s -- " + baseUrl + " " + token;
    }

    private void fill(ServerHost server, String name, String groupName, String remark) {
        String serverName = name == null ? "" : name.trim();
        String group = groupName == null ? "" : groupName.trim();
        String text = remark == null ? "" : remark.trim();
        if (serverName.isEmpty() || serverName.length() > NAME_MAX_LENGTH || group.length() > GROUP_MAX_LENGTH || text.length() > REMARK_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        server.setName(serverName);
        server.setGroupName(group);
        server.setRemark(text);
    }

    private void requireUniqueName(ServerHost server) {
        if (hostMapper.selectCount(Wrappers.<ServerHost>lambdaQuery().eq(ServerHost::getProjectId, server.getProjectId())
                .eq(ServerHost::getName, server.getName()).ne(server.getId() != null, ServerHost::getId, server.getId())) > 0) {
            throw new BusinessException(ErrorCode.SERVER_NAME_EXISTS);
        }
    }

    private Map<String, Object> toResponse(ServerHost server) {
        boolean online = metricService.isOnline(server.getId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", server.getId());
        result.put("name", server.getName());
        result.put("groupName", server.getGroupName());
        result.put("remark", server.getRemark());
        result.put("status", status(server, online));
        result.put("hostname", server.getHostname());
        result.put("ip", server.getIp());
        result.put("privateIp", server.getPrivateIp());
        result.put("os", server.getOs());
        result.put("cpuCores", server.getCpuCores());
        result.put("memoryTotal", server.getMemoryTotal());
        result.put("agentVersion", server.getAgentVersion());
        result.put("registeredAt", server.getRegisteredAt());
        result.put("lastSeenAt", metricService.lastSeen(server.getId()));
        result.put("metric", online ? metricService.hostMetric(server.getId()) : null);
        result.put("processCount", processMapper.selectCount(Wrappers.<ServerProcess>lambdaQuery().eq(ServerProcess::getServerId, server.getId())));
        return result;
    }
}
