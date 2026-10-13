package com.oaixdog.adminbackground.protocol;

import com.zfoo.protocol.anno.Protocol;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Protocol(id = 3036)
public class ProjectRoleMenuUpdateRequest {

    private Long projectId;
    private Long roleId;
    private List<Long> menuIds;
}
