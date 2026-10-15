package com.oaixdog.adminbackground.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.oaixdog.adminbackground.entity.ServerProcess;
import com.oaixdog.adminbackground.entity.ServerTask;
import com.oaixdog.adminbackground.exception.BusinessException;
import com.oaixdog.adminbackground.exception.ErrorCode;
import com.oaixdog.adminbackground.mapper.ServerTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 进程指令的执行记录：后台发起时写入「等待」，Agent 心跳领取后变为「执行中」，回传结果后为「成功 / 失败」。
 * 等待超过 10 分钟或执行超过 5 分钟没有结果的显示为「超时」，同时不再占用进程。
 */
@Service
@RequiredArgsConstructor
public class ServerTaskService {

    public static final String ACTION_START = "start";
    public static final String ACTION_STOP = "stop";
    public static final String ACTION_RESTART = "restart";
    public static final String ACTION_REMOVE = "remove";
    private static final long WAITING_TIMEOUT = 10 * 60_000L;
    private static final long RUNNING_TIMEOUT = 5 * 60_000L;
    private static final int OUTPUT_MAX_LENGTH = 65_536;
    private static final int PAGE_SIZE_MAX = 100;

    private final ServerTaskMapper serverTaskMapper;

    public long create(ServerProcess process, String action, String operator) {
        ServerTask task = new ServerTask();
        task.setProjectId(process.getProjectId());
        task.setServerId(process.getServerId());
        task.setProcessId(process.getId());
        task.setProcessName(process.getName());
        task.setAction(action);
        task.setStatus(ServerTask.STATUS_WAITING);
        task.setOutput("");
        task.setOperator(operator);
        task.setCreatedAt(System.currentTimeMillis());
        task.setDispatchedAt(0L);
        task.setFinishedAt(0L);
        serverTaskMapper.insert(task);
        return task.getId();
    }

    /** 进程 ID → 未完成（未超时）的指令 */
    public Map<Long, ServerTask> unfinished(Collection<Long> processIds) {
        Map<Long, ServerTask> result = new HashMap<>();
        if (processIds.isEmpty()) {
            return result;
        }
        long now = System.currentTimeMillis();
        serverTaskMapper.selectList(Wrappers.<ServerTask>lambdaQuery().in(ServerTask::getProcessId, processIds)
                        .in(ServerTask::getStatus, ServerTask.STATUS_WAITING, ServerTask.STATUS_RUNNING).gt(ServerTask::getCreatedAt, now - WAITING_TIMEOUT - RUNNING_TIMEOUT))
                .stream().filter(task -> !timedOut(task, now)).forEach(task -> result.put(task.getProcessId(), task));
        return result;
    }

    public void requireIdle(Collection<Long> processIds) {
        if (!unfinished(processIds).isEmpty()) {
            throw new BusinessException(ErrorCode.PROCESS_BUSY);
        }
    }

    /** Agent 领取本服务器等待中的指令 */
    public List<ServerTask> dispatch(long serverId) {
        long now = System.currentTimeMillis();
        List<ServerTask> tasks = serverTaskMapper.selectList(Wrappers.<ServerTask>lambdaQuery().eq(ServerTask::getServerId, serverId)
                .eq(ServerTask::getStatus, ServerTask.STATUS_WAITING).gt(ServerTask::getCreatedAt, now - WAITING_TIMEOUT).orderByAsc(ServerTask::getId));
        for (ServerTask task : tasks) {
            task.setStatus(ServerTask.STATUS_RUNNING);
            task.setDispatchedAt(now);
            serverTaskMapper.updateById(task);
        }
        return tasks;
    }

    /** Agent 回传结果，只接受本服务器执行中的指令 */
    public void finish(long serverId, Long taskId, boolean success, String output) {
        ServerTask task = taskId == null ? null : serverTaskMapper.selectById(taskId);
        if (task == null || task.getServerId() != serverId || task.getStatus() != ServerTask.STATUS_RUNNING) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        String text = output == null ? "" : output;
        task.setStatus(success ? ServerTask.STATUS_SUCCESS : ServerTask.STATUS_FAILED);
        task.setOutput(text.length() > OUTPUT_MAX_LENGTH ? text.substring(text.length() - OUTPUT_MAX_LENGTH) : text);
        task.setFinishedAt(System.currentTimeMillis());
        serverTaskMapper.updateById(task);
    }

    public Map<String, Object> page(long projectId, Long processId, Integer page, Integer pageSize) {
        int pageNumber = page == null ? 1 : page;
        int size = pageSize == null ? 20 : pageSize;
        if (pageNumber < 1 || size < 1 || size > PAGE_SIZE_MAX) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        long total = serverTaskMapper.selectCount(Wrappers.<ServerTask>lambdaQuery().eq(ServerTask::getProjectId, projectId)
                .eq(processId != null, ServerTask::getProcessId, processId));
        List<ServerTask> tasks = serverTaskMapper.selectList(Wrappers.<ServerTask>lambdaQuery().eq(ServerTask::getProjectId, projectId)
                .eq(processId != null, ServerTask::getProcessId, processId).orderByDesc(ServerTask::getId)
                .last("limit " + size + " offset " + (long) (pageNumber - 1) * size));
        long now = System.currentTimeMillis();
        return Map.of("total", total, "list", tasks.stream().map(task -> toResponse(task, now)).toList());
    }

    public static Map<String, Object> toResponse(ServerTask task, long now) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", task.getId());
        result.put("serverId", task.getServerId());
        result.put("processId", task.getProcessId());
        result.put("processName", task.getProcessName());
        result.put("action", task.getAction());
        result.put("status", timedOut(task, now) ? "timeout" : statusName(task.getStatus()));
        result.put("output", task.getOutput());
        result.put("operator", task.getOperator());
        result.put("createdAt", task.getCreatedAt());
        result.put("finishedAt", task.getFinishedAt());
        return result;
    }

    private static boolean timedOut(ServerTask task, long now) {
        return task.getStatus() == ServerTask.STATUS_WAITING && now - task.getCreatedAt() > WAITING_TIMEOUT
                || task.getStatus() == ServerTask.STATUS_RUNNING && now - task.getDispatchedAt() > RUNNING_TIMEOUT;
    }

    private static String statusName(int status) {
        return switch (status) {
            case ServerTask.STATUS_WAITING -> "waiting";
            case ServerTask.STATUS_RUNNING -> "running";
            case ServerTask.STATUS_SUCCESS -> "success";
            default -> "failed";
        };
    }
}
