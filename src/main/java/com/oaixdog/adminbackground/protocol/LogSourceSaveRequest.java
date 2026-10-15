package com.oaixdog.adminbackground.protocol;

import com.zfoo.protocol.anno.Protocol;
import lombok.Getter;
import lombok.Setter;

/** 首次保存时密码必填，之后留空表示沿用已保存的密码 */
@Getter
@Setter
@Protocol(id = 3091)
public class LogSourceSaveRequest {

    private Long projectId;
    private String url;
    private String database;
    private String readUser;
    private String readPassword;
}
