package com.oaixdog.adminbackground.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 数据中心查询模板：按项目保存，fields 为前端编辑的字段映射 JSON */
@Getter
@Setter
@TableName("query_template")
public class QueryTemplate {

    public static final String CATEGORY_DATA = "data";
    public static final String CATEGORY_LOG = "log";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String category;
    private String name;
    private String event;
    private String fields;
    private Long createdAt;
    private Long updatedAt;
}
