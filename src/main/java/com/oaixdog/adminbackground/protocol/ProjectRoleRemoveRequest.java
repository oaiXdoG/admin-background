package com.oaixdog.adminbackground.protocol;

import com.zfoo.protocol.anno.Protocol;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Protocol(id = 3034)
public class ProjectRoleRemoveRequest {

    private Long projectId;
    private Long roleId;
}
