package com.oaixdog.adminbackground.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("role")
public class Role {

    public static final int TYPE_PLATFORM = 1;
    public static final int TYPE_TEMPLATE = 2;
    public static final int TYPE_PROJECT = 3;
    public static final String SUPER_ADMIN_CODE = "SUPER_ADMIN";
    public static final String PROJECT_ADMIN_CODE = "PROJECT_ADMIN";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer roleType;
    private Long projectId;
    private Long templateId;
    private String name;
    private String code;
    private String description;
    private Integer status;
    private Boolean builtIn;
    private Long createdAt;
    private Long updatedAt;
}
