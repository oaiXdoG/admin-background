package com.oaixdog.adminbackground.protocol;

import com.zfoo.protocol.anno.Protocol;
import lombok.Getter;
import lombok.Setter;

/** 进程名和所属服务器创建后不能修改 */
@Getter
@Setter
@Protocol(id = 3108)
public class ServerProcessUpdateRequest {

    private Long projectId;
    private Long processId;
    private Long typeId;
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
