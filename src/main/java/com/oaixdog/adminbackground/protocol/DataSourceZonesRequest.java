package com.oaixdog.adminbackground.protocol;

import com.zfoo.protocol.anno.Protocol;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Protocol(id = 3132)
public class DataSourceZonesRequest {

    private Long projectId;
    private Long sourceId;
}
