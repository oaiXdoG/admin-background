package com.oaixdog.adminbackground.protocol;

import com.zfoo.protocol.anno.Protocol;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Protocol(id = 3064)
public class QueryTemplateRemoveRequest {

    private Long projectId;
    private Long templateId;
}
