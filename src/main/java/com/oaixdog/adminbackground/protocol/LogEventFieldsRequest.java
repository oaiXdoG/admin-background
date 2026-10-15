package com.oaixdog.adminbackground.protocol;

import com.zfoo.protocol.anno.Protocol;
import lombok.Getter;
import lombok.Setter;

/** days 默认 7，最大 90 */
@Getter
@Setter
@Protocol(id = 3077)
public class LogEventFieldsRequest {

    private Long projectId;
    private String event;
    private Integer days;
}
