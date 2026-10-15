package com.oaixdog.adminbackground.protocol;

import com.zfoo.protocol.anno.Protocol;
import lombok.Getter;
import lombok.Setter;

/** 日志源里最近 days 天有数据的事件；days 默认 7，最大 90 */
@Getter
@Setter
@Protocol(id = 3076)
public class LogEventListRequest {

    private Long projectId;
    private Integer days;
}
