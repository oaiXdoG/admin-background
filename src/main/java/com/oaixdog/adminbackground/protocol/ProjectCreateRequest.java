package com.oaixdog.adminbackground.protocol;

import com.zfoo.protocol.anno.Protocol;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Protocol(id = 3003)
public class ProjectCreateRequest {

    private String appKey;
    private String name;
    private String description;
    private List<Long> templateIds;
}
