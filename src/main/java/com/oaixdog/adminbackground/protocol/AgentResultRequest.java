package com.oaixdog.adminbackground.protocol;

import com.zfoo.protocol.anno.Protocol;
import lombok.Getter;
import lombok.Setter;

/** 指令执行结果，output 为执行输出 */
@Getter
@Setter
@Protocol(id = 3120)
public class AgentResultRequest {

    private Long taskId;
    private Boolean success;
    private String output;
}
