package com.oaixdog.adminbackground.protocol;

import com.zfoo.protocol.anno.Protocol;
import lombok.Getter;
import lombok.Setter;

/** 密码留空表示沿用已保存的密码 */
@Getter
@Setter
@Protocol(id = 3059)
public class LogStorageTestRequest {

    private String url;
    private String database;
    private String readUser;
    private String readPassword;
    private String adminUser;
    private String adminPassword;
}
