package com.oaixdog.adminbackground.protocol;

import com.zfoo.protocol.anno.Protocol;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** 与 LogQueryRequest 相同的条件，不分页，最多导出 5 万行 */
@Getter
@Setter
@Protocol(id = 3075)
public class LogExportRequest {

    private Long projectId;
    private Long startTime;
    private Long endTime;
    private List<String> events;
    private String actor;
    private String server;
    private String keyword;
    private String relation;
    private List<LogQueryCondition> conditions;
}
