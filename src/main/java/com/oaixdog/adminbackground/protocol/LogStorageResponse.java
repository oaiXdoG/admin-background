package com.oaixdog.adminbackground.protocol;

import lombok.Getter;
import lombok.Setter;

/** 日志存储配置，不返回密码，只返回是否已设置 */
@Getter
@Setter
public class LogStorageResponse {

    private String url;
    private String database;
    private String readUser;
    private boolean readPasswordSet;
    private String adminUser;
    private boolean adminPasswordSet;
}
