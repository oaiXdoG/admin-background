package com.oaixdog.adminbackground.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 进程指令的执行记录：status 1 等待、2 执行中、3 成功、4 失败 */
@Getter
@Setter
@TableName("server_task")
public class ServerTask {

    public static final int STATUS_WAITING = 1;
    public static final int STATUS_RUNNING = 2;
    public static final int STATUS_SUCCESS = 3;
    public static final int STATUS_FAILED = 4;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long serverId;
    private Long processId;
    private String processName;
    private String action;
    private Integer status;
    private String output;
    private String operator;
    private Long createdAt;
    private Long dispatchedAt;
    private Long finishedAt;
}
