package com.oaixdog.adminbackground.protocol;

import com.zfoo.protocol.anno.Protocol;
import lombok.Getter;
import lombok.Setter;

/** 按事件名新增或修改；fields 为 JSON 文本：[{ path, label, type }] */
@Getter
@Setter
@Protocol(id = 3096)
public class LogEventSaveRequest {

    private Long projectId;
    private String event;
    private String label;
    private String description;
    private String fields;
}
