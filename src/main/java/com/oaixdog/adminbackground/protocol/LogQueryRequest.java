package com.oaixdog.adminbackground.protocol;

import com.zfoo.protocol.anno.Protocol;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** 时间范围必填；events 为空表示全部事件；actor、server 精确匹配，keyword 在 data 里不区分大小写搜索；conditions 按字段路径过滤 */
@Getter
@Setter
@Protocol(id = 3074)
public class LogQueryRequest {

    private Long projectId;
    private Long startTime;
    private Long endTime;
    private List<String> events;
    private String actor;
    private String server;
    private String keyword;
    private String relation;
    private List<LogQueryCondition> conditions;
    private Integer page;
    private Integer pageSize;
}
