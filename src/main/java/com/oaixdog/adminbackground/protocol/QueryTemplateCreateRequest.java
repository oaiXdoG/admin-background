package com.oaixdog.adminbackground.protocol;

import com.zfoo.protocol.anno.Protocol;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Protocol(id = 3062)
public class QueryTemplateCreateRequest {

    private Long projectId;
    private String category;
    private String name;
    private String event;
    private String fields;
}
