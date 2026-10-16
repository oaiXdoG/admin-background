package com.oaixdog.adminbackground.protocol;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/** 业务服务器返回的结果行原样透传，由页面按模板的结果字段展示 */
@Getter
@Setter
public class DataQueryResponse {

    private long total;
    private List<Map<String, Object>> list;
    /** 按区查询时失败或已离线的区：{ zoneId, message } */
    private List<Map<String, Object>> failures = List.of();
}
