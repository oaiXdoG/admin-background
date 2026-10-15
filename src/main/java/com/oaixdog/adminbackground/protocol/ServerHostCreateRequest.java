package com.oaixdog.adminbackground.protocol;

import com.zfoo.protocol.anno.Protocol;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Protocol(id = 3101)
public class ServerHostCreateRequest {

    private Long projectId;
    private String name;
    private String groupName;
    private String remark;
}
