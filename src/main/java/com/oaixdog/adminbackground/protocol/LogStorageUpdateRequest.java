package com.oaixdog.adminbackground.protocol;

import com.zfoo.protocol.anno.Protocol;
import lombok.Getter;
import lombok.Setter;

/** 管理密码留空表示沿用已保存的密码 */
@Getter
@Setter
@Protocol(id = 3058)
public class LogStorageUpdateRequest {

    private String url;
    private String database;
    private String adminUser;
    private String adminPassword;
}
