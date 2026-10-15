package com.oaixdog.adminbackground.protocol;

import com.zfoo.protocol.anno.Protocol;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** action：start、stop、restart */
@Getter
@Setter
@Protocol(id = 3110)
public class ServerProcessOperateRequest {

    private Long projectId;
    private List<Long> processIds;
    private String action;
}
