package com.oaixdog.adminbackground.protocol;

import com.zfoo.protocol.anno.Protocol;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Protocol(id = 3124)
public class ServerProcessTypeRemoveRequest {

    private Long projectId;
    private Long typeId;
}
