package com.oaixdog.adminbackground.protocol;

import com.zfoo.protocol.anno.Protocol;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Protocol(id = 3115)
public class ServerTemplateRemoveRequest {

    private Long projectId;
    private Long templateId;
}
