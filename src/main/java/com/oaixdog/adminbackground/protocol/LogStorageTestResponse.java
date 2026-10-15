package com.oaixdog.adminbackground.protocol;

import lombok.Getter;
import lombok.Setter;

/** 测试管理账号的结果；保留天数从表的 TTL 中读出，读不到时为空 */
@Getter
@Setter
public class LogStorageTestResponse {

    private boolean adminOk;
    private String adminMessage;
    private String version;
    private Integer retentionDays;
}
