package com.oaixdog.adminbackground.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("account")
public class Account {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String account;
    private String password;
    private String username;
    private Boolean admin;
    private Long lastProjectId;
}
