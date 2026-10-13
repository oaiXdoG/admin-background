package com.oaixdog.adminbackground.protocol;

import com.zfoo.protocol.anno.Protocol;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Protocol(id = 3039)
public class ProjectMemberAddRequest {

    private Long projectId;
    private String account;
    private Long roleId;
}
