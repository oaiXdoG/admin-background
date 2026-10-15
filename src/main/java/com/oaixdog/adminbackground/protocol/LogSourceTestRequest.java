package com.oaixdog.adminbackground.protocol;

import com.zfoo.protocol.anno.Protocol;
import lombok.Getter;
import lombok.Setter;

/** 用表单里的值测试：密码留空时使用已保存的密码 */
@Getter
@Setter
@Protocol(id = 3094)
public class LogSourceTestRequest {

    private Long projectId;
    private String url;
    private String database;
    private String readUser;
    private String readPassword;
}
