package com.oaixdog.adminbackground.protocol;

import com.zfoo.protocol.anno.Protocol;
import lombok.Getter;
import lombok.Setter;

/** templateId 为空表示新建 */
@Getter
@Setter
@Protocol(id = 3114)
public class ServerTemplateSaveRequest {

    private Long projectId;
    private Long templateId;
    private String name;
    private String options;
}
