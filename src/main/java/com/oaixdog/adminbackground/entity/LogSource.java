package com.oaixdog.adminbackground.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 日志源：项目查询日志用的 ClickHouse 库（只读账号），read_password 加密保存；每个项目一个 */
@Getter
@Setter
@TableName("log_source")
public class LogSource {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String url;
    private String database;
    private String readUser;
    private String readPassword;
    private Long createdAt;
    private Long updatedAt;
}
