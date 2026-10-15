package com.oaixdog.adminbackground.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** JVM 参数模板：进程启动时模板参数在前，进程自己的参数在后 */
@Getter
@Setter
@TableName("server_jvm_template")
public class ServerJvmTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String name;
    private String options;
    private Long createdAt;
    private Long updatedAt;
}
