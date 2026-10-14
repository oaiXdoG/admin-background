package com.oaixdog.adminbackground.protocol;

import com.zfoo.protocol.anno.Protocol;
import lombok.Getter;
import lombok.Setter;

/** field 是模板字段的 key；数值、时间（毫秒）、布尔值（true/false）都以文本传递，介于时 value2 为上限 */
@Getter
@Setter
@Protocol(id = 3066)
public class DataQueryCondition {

    private String field;
    private String operator;
    private String value;
    private String value2;
}
