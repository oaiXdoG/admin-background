package com.oaixdog.adminbackground.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 托管进程：Agent 按这些字段生成 systemd 服务 adm-<name>；mainClass 不为空时用 -cp classpath mainClass 启动，否则 -jar jar；修改后 configVersion 加 1，重启后生效 */
@Getter
@Setter
@TableName("server_process")
public class ServerProcess {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long serverId;
    private Long typeId;
    private String name;
    private String remark;
    private String workDir;
    private String javaPath;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long templateId;
    private String jvmOptions;
    private String jar;
    private String classpath;
    private String mainClass;
    private String programArgs;
    private String env;
    private String runUser;
    private Integer port;
    private Integer configVersion;
    private Long createdAt;
    private Long updatedAt;
}
