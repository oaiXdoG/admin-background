package com.oaixdog.adminbackground.protocol;

import com.zfoo.protocol.anno.Protocol;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** 与 DataQueryRequest 相同但不分页，最多导出 5 万行 */
@Getter
@Setter
@Protocol(id = 3089)
public class DataQueryExportRequest {

    private Long projectId;
    private Long templateId;
    /** 按区自动发现的数据源：要查询的区 */
    private List<Long> zoneIds;
    private String params;
}
