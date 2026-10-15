package com.oaixdog.adminbackground.protocol;

import com.zfoo.protocol.anno.Protocol;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** Agent 每 5 秒上报：服务器信息、主机指标和本机托管进程的状态 */
@Getter
@Setter
@Protocol(id = 3117)
public class AgentHeartbeatRequest {

    private String hostname;
    private String os;
    /** 本机网卡的内网 IPv4，多个用逗号分隔 */
    private String privateIp;
    private Integer cpuCores;
    private Long memoryTotal;
    private String agentVersion;
    private AgentHostMetric host;
    private List<AgentProcessMetric> processes;
    /** 为 true 表示 Agent 用长轮询领取指令，心跳不再下发指令 */
    private Boolean longPoll;
}
