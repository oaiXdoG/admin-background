package com.oaixdog.adminbackground.protocol;

import com.zfoo.protocol.anno.Protocol;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Protocol(id = 3102)
public class ServerHostUpdateRequest {

    private Long projectId;
    private Long serverId;
    private String name;
    private String groupName;
    private String remark;
}
