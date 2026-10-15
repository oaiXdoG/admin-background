package com.oaixdog.adminbackground.protocol;

import com.zfoo.protocol.anno.Protocol;
import lombok.Getter;
import lombok.Setter;

/** 发布包分块上传：第一块 uploadId 为空，data 为该块的 base64 */
@Getter
@Setter
@Protocol(id = 3125)
public class ServerDeployUploadRequest {

    private Long projectId;
    private Long processId;
    private String uploadId;
    private Integer index;
    private Integer total;
    private String data;
}
