package com.oaixdog.adminbackground.protocol;

import com.zfoo.protocol.anno.Protocol;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** params 为参数值 JSON 对象文本，按模板的参数定义校验和转换类型 */
@Getter
@Setter
@Protocol(id = 3088)
public class DataQueryRequest {

    private Long projectId;
    private Long templateId;
    /** 按区自动发现的数据源：要查询的区 */
    private List<Long> zoneIds;
    private String params;
    private Integer page;
    private Integer pageSize;
}
