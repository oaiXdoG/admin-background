package com.oaixdog.adminbackground.protocol;

import com.zfoo.protocol.anno.Protocol;
import lombok.Getter;
import lombok.Setter;

/** op 为 list / read / write / delete，path 相对工作目录 */
@Getter
@Setter
@Protocol(id = 3127)
public class ServerFileRequest {

    private Long projectId;
    private Long processId;
    private String op;
    private String path;
    private String content;
}
