package com.oaixdog.adminbackground.protocol;

import com.zfoo.protocol.anno.Protocol;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Protocol(id = 3097)
public class LogEventRemoveRequest {

    private Long projectId;
    private String event;
}
