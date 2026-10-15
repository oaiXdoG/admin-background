package com.oaixdog.adminbackground.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.oaixdog.adminbackground.entity.ServerHost;
import com.oaixdog.adminbackground.entity.ServerProcess;
import com.oaixdog.adminbackground.entity.ServerTask;
import com.oaixdog.adminbackground.exception.BusinessException;
import com.oaixdog.adminbackground.exception.ErrorCode;
import com.oaixdog.adminbackground.mapper.ServerHostMapper;
import com.oaixdog.adminbackground.mapper.ServerProcessMapper;
import com.oaixdog.adminbackground.protocol.AgentHeartbeatRequest;
import com.oaixdog.adminbackground.protocol.AgentHostMetric;
import com.oaixdog.adminbackground.protocol.AgentProcessMetric;
import com.oaixdog.adminbackground.protocol.AgentRegisterRequest;
import com.zfoo.protocol.util.JsonUtils;
import com.zfoo.protocol.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Agent 接口：注册用一次性安装令牌换取服务器 ID 和密钥；心跳和结果回传用密钥签名
 * （HMAC-SHA256(密钥, 时间戳 + "\n" + SHA-256(请求体))，时间戳与服务器相差不超过 5 分钟）。
 */
@Service
@RequiredArgsConstructor
public class AgentService {

    public static final String INSTALL_KEY = "server:install:";
    /** 路由解析时写入的请求体 SHA-256（十六进制），见 Application.parseAgentRequest */
    public static final String BODY_DIGEST_HEADER = "X-Agent-Body-Digest";
    private static final long SIGNATURE_WINDOW = 5 * 60_000L;
    private static final int TEXT_MAX_LENGTH = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ServerHostMapper hostMapper;
    private final ServerProcessMapper processMapper;
    private final ServerProcessService processService;
    private final ServerTaskService taskService;
    private final ServerDeployService deployService;
    private final ServerFileService fileService;
    private final AgentWaitService waitService;
    private final ServerMetricService metricService;
    private final SecretCipher secretCipher;
    private final RedisService redisService;

    /** 令牌只能用一次；已接入的服务器重新注册会更换密钥，旧 Agent 随即失效 */
    public Map<String, Object> register(AgentRegisterRequest request, String ip) {
        String token = request.getToken() == null ? "" : request.getToken().trim();
        String serverId = token.isEmpty() ? null : redisService.get(INSTALL_KEY + token);
        if (serverId == null || !redisService.delete(INSTALL_KEY + token)) {
            throw new BusinessException(ErrorCode.AGENT_TOKEN_INVALID);
        }
        ServerHost server = hostMapper.selectById(Long.valueOf(serverId));
        if (server == null) {
            throw new BusinessException(ErrorCode.AGENT_TOKEN_INVALID);
        }
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String secret = HexFormat.of().formatHex(bytes);
        long now = System.currentTimeMillis();
        server.setSecret(secretCipher.encrypt(secret));
        updateInfo(server, request.getHostname(), request.getOs(), request.getPrivateIp(), request.getCpuCores(), request.getMemoryTotal(), request.getAgentVersion(), ip);
        server.setRegisteredAt(now);
        server.setUpdatedAt(now);
        hostMapper.updateById(server);
        return Map.of("serverId", server.getId(), "secret", secret);
    }

    public ServerHost authenticate(String serverIdHeader, String timestampHeader, String signature, String bodyDigest) {
        try {
            long serverId = Long.parseLong(serverIdHeader);
            long timestamp = Long.parseLong(timestampHeader);
            ServerHost server = hostMapper.selectById(serverId);
            String secret = server == null || StringUtils.isBlank(server.getSecret()) ? null : secretCipher.decrypt(server.getSecret());
            if (secret != null && signature != null && bodyDigest != null && Math.abs(System.currentTimeMillis() - timestamp) <= SIGNATURE_WINDOW) {
                String expected = BusinessClient.sign(secret, timestamp + "\n" + bodyDigest);
                if (MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8))) {
                    return server;
                }
            }
        } catch (NumberFormatException exception) {
            // 请求头格式不对按签名无效处理
        }
        throw new BusinessException(ErrorCode.AGENT_SIGNATURE_INVALID);
    }

    public static String digest(String body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    /** 保存指标，返回本服务器的进程定义和本次领取的指令 */
    public Map<String, Object> heartbeat(ServerHost server, AgentHeartbeatRequest request, String ip) {
        if (updateInfo(server, request.getHostname(), request.getOs(), request.getPrivateIp(), request.getCpuCores(), request.getMemoryTotal(), request.getAgentVersion(), ip)) {
            server.setUpdatedAt(System.currentTimeMillis());
            hostMapper.updateById(server);
        }
        Set<Long> owned = processMapper.selectList(Wrappers.<ServerProcess>lambdaQuery().eq(ServerProcess::getServerId, server.getId()))
                .stream().map(ServerProcess::getId).collect(Collectors.toSet());
        List<AgentProcessMetric> processes = request.getProcesses() == null ? List.of() : request.getProcesses();
        Map<Long, Map<String, Object>> processMetrics = new HashMap<>();
        for (AgentProcessMetric metric : processes) {
            if (metric != null && owned.contains(metric.getProcessId())) {
                processMetrics.put(metric.getProcessId(), processMetric(metric));
            }
        }
        metricService.saveHeartbeat(server, hostMetric(request.getHost()), processMetrics);
        // 0.3.0 起 Agent 用长轮询（/api/agent/wait）领取指令，心跳只上报指标；旧版 Agent 仍随心跳领取
        if (Boolean.TRUE.equals(request.getLongPoll())) {
            return Map.of("processes", processService.agentDefinitions(server), "tasks", List.of(), "fileOps", List.of());
        }
        Map<String, Object> commands = commands(server.getId());
        return Map.of("processes", processService.agentDefinitions(server), "tasks", commands.get("tasks"), "fileOps", commands.get("fileOps"));
    }

    /** 长轮询：有指令或文件操作时通过 reply 返回 { tasks, fileOps }，最长等 25 秒 */
    public void await(ServerHost server, Consumer<Map<String, Object>> reply) {
        waitService.await(server.getId(), () -> commands(server.getId()), reply);
    }

    private Map<String, Object> commands(long serverId) {
        List<Map<String, Object>> tasks = taskService.dispatch(serverId).stream().map(AgentService::toTask).toList();
        return Map.of("tasks", tasks, "fileOps", fileService.dispatch(serverId));
    }

    public void result(ServerHost server, Long taskId, Boolean success, String output) {
        deployService.cleanup(taskService.finish(server.getId(), taskId, Boolean.TRUE.equals(success), output));
    }

    /** 服务器信息有变化时更新，返回是否有变化 */
    private static boolean updateInfo(ServerHost server, String hostname, String os, String privateIp, Integer cpuCores, Long memoryTotal, String agentVersion, String ip) {
        String newHostname = truncate(hostname);
        String newOs = truncate(os);
        String newPrivateIp = truncate(privateIp);
        int newCpuCores = cpuCores == null ? 0 : cpuCores;
        long newMemoryTotal = memoryTotal == null ? 0L : memoryTotal;
        String newAgentVersion = truncate(agentVersion).length() > 32 ? truncate(agentVersion).substring(0, 32) : truncate(agentVersion);
        String newIp = ip == null ? "" : ip.length() > 64 ? ip.substring(0, 64) : ip;
        boolean changed = !Objects.equals(server.getHostname(), newHostname) || !Objects.equals(server.getOs(), newOs)
                || !Objects.equals(server.getCpuCores(), newCpuCores) || !Objects.equals(server.getMemoryTotal(), newMemoryTotal)
                || !Objects.equals(server.getAgentVersion(), newAgentVersion) || !Objects.equals(server.getIp(), newIp)
                || !Objects.equals(server.getPrivateIp(), newPrivateIp);
        server.setHostname(newHostname);
        server.setOs(newOs);
        server.setPrivateIp(newPrivateIp);
        server.setCpuCores(newCpuCores);
        server.setMemoryTotal(newMemoryTotal);
        server.setAgentVersion(newAgentVersion);
        server.setIp(newIp);
        return changed;
    }

    private static String truncate(String text) {
        String value = text == null ? "" : text.trim();
        return value.length() > TEXT_MAX_LENGTH ? value.substring(0, TEXT_MAX_LENGTH) : value;
    }

    private static Map<String, Object> hostMetric(AgentHostMetric host) {
        Map<String, Object> result = new HashMap<>();
        if (host != null) {
            result.put("cpu", host.getCpu());
            result.put("load1", host.getLoad1());
            result.put("memoryUsed", host.getMemoryUsed());
            result.put("memoryTotal", host.getMemoryTotal());
            result.put("diskUsed", host.getDiskUsed());
            result.put("diskTotal", host.getDiskTotal());
            result.put("netIn", host.getNetIn());
            result.put("netOut", host.getNetOut());
            result.put("packetsIn", host.getPacketsIn());
            result.put("packetsOut", host.getPacketsOut());
            result.put("tcp", host.getTcp());
            result.put("uptime", host.getUptime());
        }
        return result;
    }

    private static Map<String, Object> processMetric(AgentProcessMetric metric) {
        Map<String, Object> result = new HashMap<>();
        result.put("state", truncate(metric.getState()));
        result.put("pid", metric.getPid());
        result.put("cpu", metric.getCpu());
        result.put("memory", metric.getMemory());
        result.put("threads", metric.getThreads());
        result.put("tcp", metric.getTcp());
        result.put("startedAt", metric.getStartedAt());
        result.put("configVersion", metric.getConfigVersion());
        return result;
    }

    private static Map<String, Object> toTask(ServerTask task) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", task.getId());
        result.put("processId", task.getProcessId());
        result.put("processName", task.getProcessName());
        result.put("action", task.getAction());
        result.put("param", StringUtils.isBlank(task.getParam()) ? null : JsonUtils.string2Object(task.getParam(), LinkedHashMap.class));
        return result;
    }
}
