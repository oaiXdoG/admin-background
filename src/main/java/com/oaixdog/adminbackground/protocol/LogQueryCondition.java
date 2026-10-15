package com.oaixdog.adminbackground.protocol;

import com.zfoo.protocol.anno.Protocol;
import lombok.Getter;
import lombok.Setter;

/** path 为日志自带列（time、server、actor、event、logId、retention）或 data.<路径>；type 决定比较方式；值以文本传递 */
@Getter
@Setter
@Protocol(id = 3066)
public class LogQueryCondition {

    private String path;
    private String type;
    private String operator;
    private String value;
    private String value2;
}
