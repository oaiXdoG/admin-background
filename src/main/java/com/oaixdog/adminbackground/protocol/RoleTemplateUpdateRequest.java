package com.oaixdog.adminbackground.protocol;

import com.zfoo.protocol.anno.Protocol;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Protocol(id = 3027)
public class RoleTemplateUpdateRequest {

    private Long roleId;
    private String name;
    private String code;
    private String description;
    private Integer status;
}
