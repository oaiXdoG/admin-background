package com.oaixdog.adminbackground.protocol;

import com.zfoo.protocol.anno.Protocol;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Protocol(id = 3032)
public class ProjectRoleCreateRequest {

    private Long projectId;
    private String name;
    private String code;
    private String description;
    private Integer status;
}
