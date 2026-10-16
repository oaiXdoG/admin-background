package com.oaixdog.adminbackground.protocol;

import com.zfoo.protocol.anno.Protocol;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Protocol(id = 3079)
public class DataSourceCreateRequest {

    private Long projectId;
    private String name;
    private String type;
    /** 按区自动发现的连接方式：inner（默认）/ public */
    private String network;
    private String url;
    private String secret;
    private Integer timeoutMs;
    private String remark;
}
