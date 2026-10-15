package com.oaixdog.adminbackground.protocol;

import com.zfoo.protocol.anno.Protocol;
import lombok.Getter;
import lombok.Setter;

/** 主机指标：cpu 为百分比，内存和磁盘为字节，网络为字节/秒，包为个/秒，tcp 为已建立的 TCP 连接数，uptime 为秒 */
@Getter
@Setter
@Protocol(id = 3118)
public class AgentHostMetric {

    private Double cpu;
    private Double load1;
    private Long memoryUsed;
    private Long memoryTotal;
    private Long diskUsed;
    private Long diskTotal;
    private Long netIn;
    private Long netOut;
    private Long packetsIn;
    private Long packetsOut;
    private Integer tcp;
    private Long uptime;
}
