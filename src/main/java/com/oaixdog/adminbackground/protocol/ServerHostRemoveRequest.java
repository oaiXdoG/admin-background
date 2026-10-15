package com.oaixdog.adminbackground.protocol;

import com.zfoo.protocol.anno.Protocol;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Protocol(id = 3103)
public class ServerHostRemoveRequest {

    private Long projectId;
    private Long serverId;
}
