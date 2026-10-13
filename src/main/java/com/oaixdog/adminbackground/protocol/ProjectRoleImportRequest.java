package com.oaixdog.adminbackground.protocol;

import com.zfoo.protocol.anno.Protocol;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Protocol(id = 3037)
public class ProjectRoleImportRequest {

    private Long projectId;
    private List<Long> templateIds;
}
