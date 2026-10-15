package com.oaixdog.adminbackground.protocol;

import com.zfoo.protocol.anno.Protocol;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Protocol(id = 3126)
public class ServerDeployRequest {

    private Long projectId;
    private Long processId;
    private String uploadId;
    private String fileName;
    private String remark;
}
