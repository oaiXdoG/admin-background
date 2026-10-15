package com.oaixdog.adminbackground.protocol;

import com.zfoo.protocol.anno.Protocol;
import lombok.Getter;
import lombok.Setter;

/** Agent 长轮询领取指令 */
@Getter
@Setter
@Protocol(id = 3131)
public class AgentWaitRequest {

    private String agentVersion;
}
