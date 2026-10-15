package com.oaixdog.adminbackground.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 事件字典：事件的中文名、说明和字段说明（fields 为 JSON：path、label、type），只影响展示，不影响能否查询 */
@Getter
@Setter
@TableName("log_event")
public class LogEvent {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String event;
    private String label;
    private String description;
    private String fields;
    private Long createdAt;
    private Long updatedAt;
}
