package com.oaixdog.adminbackground.protocol;

import com.zfoo.protocol.anno.Protocol;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Protocol(id = 3018)
public class ProjectUpdateRequest {

    private Long projectId;
    private String appKey;
    private String name;
}
