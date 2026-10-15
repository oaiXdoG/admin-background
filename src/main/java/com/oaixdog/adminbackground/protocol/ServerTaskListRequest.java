package com.oaixdog.adminbackground.protocol;

import com.zfoo.protocol.anno.Protocol;
import lombok.Getter;
import lombok.Setter;

/** processId 为空表示本项目全部进程 */
@Getter
@Setter
@Protocol(id = 3112)
public class ServerTaskListRequest {

    private Long projectId;
    private Long processId;
    private Integer page;
    private Integer pageSize;
}
