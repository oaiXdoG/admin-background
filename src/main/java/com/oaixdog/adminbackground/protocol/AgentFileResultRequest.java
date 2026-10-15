package com.oaixdog.adminbackground.protocol;

import com.zfoo.protocol.anno.Protocol;
import lombok.Getter;
import lombok.Setter;

/** Agent 回传文件操作结果，data 为 JSON 文本 */
@Getter
@Setter
@Protocol(id = 3130)
public class AgentFileResultRequest {

    private String opId;
    private Boolean success;
    private String message;
    private String data;
}
