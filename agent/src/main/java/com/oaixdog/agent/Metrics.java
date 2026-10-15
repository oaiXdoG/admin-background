package com.oaixdog.agent;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;
import oshi.software.os.InternetProtocolStats;
import oshi.software.os.OSFileStore;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** 用 OSHI 采集主机和进程指标；CPU 和网络取两次采集之间的变化，第一次采集为 0 */
final class Metrics {

    private final SystemInfo systemInfo = new SystemInfo();
    private final HardwareAbstractionLayer hardware = systemInfo.getHardware();
    private final OperatingSystem os = systemInfo.getOperatingSystem();
    private final CentralProcessor processor = hardware.getProcessor();
    private long[] cpuTicks = processor.getSystemCpuLoadTicks();
    private long netIn = -1;
    private long netOut = -1;
    private long packetsIn = -1;
    private long packetsOut = -1;
    private long netTime;
    private final Map<Integer, OSProcess> processSnapshots = new HashMap<>();
    private static final Pattern VIRTUAL = Pattern.compile("^(lo|docker|br-|veth|virbr|cni|flannel|cali|tun|tap)");

    String hostname() {
        return os.getNetworkParams().getHostName();
    }

    String osName() {
        return os.toString();
    }

    /** 本机网卡的内网 IPv4（跳过回环和 Docker、bridge、veth 等虚拟网卡），多个用逗号分隔 */
    String privateIps() {
        List<String> result = new ArrayList<>();
        for (NetworkIF network : hardware.getNetworkIFs()) {
            if (!VIRTUAL.matcher(network.getName()).find()) {
                result.addAll(Arrays.asList(network.getIPv4addr()));
            }
        }
        return String.join(",", result);
    }

    int cpuCores() {
        return processor.getLogicalProcessorCount();
    }

    long memoryTotal() {
        return hardware.getMemory().getTotal();
    }

    synchronized Map<String, Object> host() {
        double cpu = processor.getSystemCpuLoadBetweenTicks(cpuTicks) * 100;
        cpuTicks = processor.getSystemCpuLoadTicks();
        GlobalMemory memory = hardware.getMemory();
        long diskTotal = 0;
        long diskUsed = 0;
        for (OSFileStore store : os.getFileSystem().getFileStores()) {
            if ("/".equals(store.getMount())) {
                diskTotal = store.getTotalSpace();
                diskUsed = store.getTotalSpace() - store.getUsableSpace();
            }
        }
        long in = 0;
        long out = 0;
        long packetIn = 0;
        long packetOut = 0;
        for (NetworkIF network : hardware.getNetworkIFs()) {
            if (!network.getName().startsWith("lo")) {
                network.updateAttributes();
                in += network.getBytesRecv();
                out += network.getBytesSent();
                packetIn += network.getPacketsRecv();
                packetOut += network.getPacketsSent();
            }
        }
        long now = System.currentTimeMillis();
        double seconds = (now - netTime) / 1000.0;
        long inRate = netIn < 0 || seconds <= 0 ? 0 : Math.max(0, Math.round((in - netIn) / seconds));
        long outRate = netOut < 0 || seconds <= 0 ? 0 : Math.max(0, Math.round((out - netOut) / seconds));
        long packetInRate = packetsIn < 0 || seconds <= 0 ? 0 : Math.max(0, Math.round((packetIn - packetsIn) / seconds));
        long packetOutRate = packetsOut < 0 || seconds <= 0 ? 0 : Math.max(0, Math.round((packetOut - packetsOut) / seconds));
        netIn = in;
        netOut = out;
        packetsIn = packetIn;
        packetsOut = packetOut;
        netTime = now;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cpu", round(cpu));
        result.put("load1", round(processor.getSystemLoadAverage(1)[0]));
        result.put("memoryUsed", memory.getTotal() - memory.getAvailable());
        result.put("memoryTotal", memory.getTotal());
        result.put("diskUsed", diskUsed);
        result.put("diskTotal", diskTotal);
        result.put("netIn", inRate);
        result.put("netOut", outRate);
        result.put("packetsIn", packetInRate);
        result.put("packetsOut", packetOutRate);
        result.put("uptime", os.getSystemUptime());
        return result;
    }

    /** 已建立的 TCP 连接数：pid → 连接数，-1 为整机合计（同一连接只算一次） */
    synchronized Map<Integer, Integer> tcpConnections() {
        Map<Integer, Integer> result = new HashMap<>();
        for (InternetProtocolStats.IPConnection connection : os.getInternetProtocolStats().getConnections()) {
            if (connection.getType().startsWith("tcp") && connection.getState() == InternetProtocolStats.TcpState.ESTABLISHED) {
                result.merge(-1, 1, Integer::sum);
                result.merge(connection.getowningProcessId(), 1, Integer::sum);
            }
        }
        return result;
    }

    /** 进程不存在时返回 null；cpu 为占整机的百分比 */
    synchronized Map<String, Object> process(int pid) {
        OSProcess current = pid > 0 ? os.getProcess(pid) : null;
        if (current == null) {
            processSnapshots.remove(pid);
            return null;
        }
        OSProcess previous = processSnapshots.put(pid, current);
        double load = previous == null ? 0 : current.getProcessCpuLoadBetweenTicks(previous);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cpu", round(load * 100 / cpuCores()));
        result.put("memory", current.getResidentSetSize());
        result.put("threads", current.getThreadCount());
        result.put("startedAt", current.getStartTime());
        return result;
    }

    /** 只保留本轮还在的进程快照 */
    synchronized void retainProcesses(Collection<Integer> pids) {
        processSnapshots.keySet().retainAll(pids);
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
