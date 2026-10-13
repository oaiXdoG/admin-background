package com.oaixdog.adminbackground.protocol;

import com.zfoo.protocol.anno.Protocol;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Protocol(id = 3040)
public class ProjectMemberUpdateRequest {

    private Long projectId;
    private Long accountId;
    private Long roleId;
}
