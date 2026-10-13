package com.oaixdog.adminbackground.protocol;

import com.zfoo.protocol.anno.Protocol;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Protocol(id = 3035)
public class ProjectRoleMenuListRequest {

    private Long projectId;
    private Long roleId;
}
