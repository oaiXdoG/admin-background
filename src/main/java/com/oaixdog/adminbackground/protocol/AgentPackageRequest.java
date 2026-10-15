package com.oaixdog.adminbackground.protocol;

import com.zfoo.protocol.anno.Protocol;
import lombok.Getter;
import lombok.Setter;

/** Agent 下载部署指令的发布包第 index 块 */
@Getter
@Setter
@Protocol(id = 3129)
public class AgentPackageRequest {

    private Long taskId;
    private Integer index;
}
