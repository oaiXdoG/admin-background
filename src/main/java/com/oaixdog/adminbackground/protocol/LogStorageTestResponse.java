package com.oaixdog.adminbackground.protocol;

import lombok.Getter;
import lombok.Setter;

/** 测试连接结果：未配置管理账号时 adminOk 为空；保留天数从表的 TTL 中读出，读不到时为空 */
@Getter
@Setter
public class LogStorageTestResponse {

    private boolean readOk;
    private String readMessage;
    private Boolean adminOk;
    private String adminMessage;
    private String version;
    private Integer retentionDays;
}
