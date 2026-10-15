package com.oaixdog.adminbackground.protocol;

import com.zfoo.protocol.anno.Protocol;
import lombok.Getter;
import lombok.Setter;

/** 进程状态：state 为 systemd 的 ActiveState；configVersion 为启动时的配置版本 */
@Getter
@Setter
@Protocol(id = 3119)
public class AgentProcessMetric {

    private Long processId;
    private String state;
    private Long pid;
    private Double cpu;
    private Long memory;
    private Integer threads;
    private Integer tcp;
    private Long startedAt;
    private Integer configVersion;
}
