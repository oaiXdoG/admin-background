package com.oaixdog.adminbackground.protocol;

import com.zfoo.protocol.anno.Protocol;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Protocol(id = 3107)
public class ServerProcessCreateRequest {

    private Long projectId;
    private Long serverId;
    private String name;
    private String remark;
    private String workDir;
    private String javaPath;
    private Long templateId;
    private String jvmOptions;
    private String jar;
    private String classpath;
    private String mainClass;
    private String programArgs;
    private String env;
    private String runUser;
    private Integer port;
}
