package com.oaixdog.adminbackground.service;

import com.zfoo.scheduler.manager.SchedulerBus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Agent 长轮询领取指令：Agent 的等待请求先检查一次，有指令立即返回；没有就挂起，
 * 页面发起指令或文件操作时（wake）立即返回，25 秒仍没有则返回空，Agent 随即发起下一次等待。
 * 每台服务器只保留一个挂起的请求，新的请求到来时旧的返回空。
 */
@Service
public class AgentWaitService {

    private static final long WAIT_SECONDS = 25;
    private static final Map<String, Object> EMPTY = Map.of("tasks", List.of(), "fileOps", List.of());

    private final Map<Long, Waiter> waiters = new ConcurrentHashMap<>();
    private final Map<Long, Object> locks = new ConcurrentHashMap<>();

    /** poll 领取本服务器的指令和文件操作；reply 把结果写回 Agent 的请求 */
    private record Waiter(Supplier<Map<String, Object>> poll, Consumer<Map<String, Object>> reply, ScheduledFuture<?> timeout) {
    }

    public void await(long serverId, Supplier<Map<String, Object>> poll, Consumer<Map<String, Object>> reply) {
        Waiter previous;
        synchronized (lock(serverId)) {
            Map<String, Object> data = poll.get();
            if (!isEmpty(data)) {
                reply.accept(data);
                return;
            }
            Waiter[] holder = new Waiter[1];
            ScheduledFuture<?> timeout = SchedulerBus.schedule(() -> expire(serverId, holder[0]), WAIT_SECONDS, TimeUnit.SECONDS);
            holder[0] = new Waiter(poll, reply, timeout);
            previous = waiters.put(serverId, holder[0]);
        }
        if (previous != null) {
            previous.timeout().cancel(false);
            previous.reply().accept(EMPTY);
        }
    }

    /** 有新的指令或文件操作时调用，挂起的请求立即领取并返回 */
    public void wake(long serverId) {
        Waiter waiter;
        synchronized (lock(serverId)) {
            waiter = waiters.remove(serverId);
        }
        if (waiter != null) {
            waiter.timeout().cancel(false);
            waiter.reply().accept(waiter.poll().get());
        }
    }

    private void expire(long serverId, Waiter waiter) {
        boolean removed;
        synchronized (lock(serverId)) {
            removed = waiter != null && waiters.remove(serverId, waiter);
        }
        if (removed) {
            waiter.reply().accept(EMPTY);
        }
    }

    private Object lock(long serverId) {
        return locks.computeIfAbsent(serverId, id -> new Object());
    }

    private static boolean isEmpty(Map<String, Object> data) {
        return data.values().stream().allMatch(value -> value instanceof List<?> list && list.isEmpty());
    }
}
