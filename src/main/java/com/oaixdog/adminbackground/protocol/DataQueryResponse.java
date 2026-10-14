package com.oaixdog.adminbackground.protocol;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/** 每行包含 logId、time（毫秒）、server、actor、event、retention 和 data（业务字段对象） */
@Getter
@Setter
public class DataQueryResponse {

    private String database;
    private long total;
    private List<Map<String, Object>> list;
}
