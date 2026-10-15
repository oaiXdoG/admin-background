package com.oaixdog.adminbackground.protocol;

import com.zfoo.protocol.anno.Protocol;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Protocol(id = 3109)
public class ServerProcessRemoveRequest {

    private Long projectId;
    private Long processId;
}
