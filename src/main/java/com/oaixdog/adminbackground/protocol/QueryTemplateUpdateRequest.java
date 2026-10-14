package com.oaixdog.adminbackground.protocol;

import com.zfoo.protocol.anno.Protocol;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Protocol(id = 3063)
public class QueryTemplateUpdateRequest {

    private Long projectId;
    private Long templateId;
    private String category;
    private String name;
    private String event;
    private String fields;
}
