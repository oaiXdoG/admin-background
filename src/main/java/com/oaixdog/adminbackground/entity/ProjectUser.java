package com.oaixdog.adminbackground.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("project_user")
public class ProjectUser {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long accountId;
    private Long roleId;
}
