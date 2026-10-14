package com.oaixdog.adminbackground.protocol;

import com.zfoo.protocol.anno.Protocol;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** 时间为毫秒时间戳，范围为 [startTime, endTime)；relation 为 and 或 or；page 从 1 开始 */
@Getter
@Setter
@Protocol(id = 3065)
public class DataQueryRequest {

    private Long projectId;
    private Long templateId;
    private Long startTime;
    private Long endTime;
    private String relation;
    private List<DataQueryCondition> conditions;
    private Integer page;
    private Integer pageSize;
}
