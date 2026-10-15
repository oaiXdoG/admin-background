package com.oaixdog.adminbackground.protocol;

import com.zfoo.protocol.anno.Protocol;
import lombok.Getter;
import lombok.Setter;

/** 监控曲线：processId 为空时查主机；时间为毫秒，step 为聚合粒度（秒） */
@Getter
@Setter
@Protocol(id = 3121)
public class ServerMetricQueryRequest {

    private Long projectId;
    private Long serverId;
    private Long processId;
    private Long startTime;
    private Long endTime;
    private Integer step;
}
